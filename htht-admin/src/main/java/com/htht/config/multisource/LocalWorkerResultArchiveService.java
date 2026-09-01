package com.htht.config.multisource;

import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public final class LocalWorkerResultArchiveService {
    private static final int RESPONSE_BUFFER_SIZE = 64 * 1024;
    private final Path workRoot;

    public LocalWorkerResultArchiveService(MultiSourceWorkerProperties properties) {
        this.workRoot = properties.toLocalProcessProperties().workRoot().toAbsolutePath().normalize();
    }

    public void write(TaskDetailView detail, AccessSubject subject, HttpServletResponse response) throws IOException {
        write(detail, subject, null, response);
    }

    public void write(TaskDetailView detail, AccessSubject subject, String downloadFileName,
                      HttpServletResponse response) throws IOException {
        if (detail == null || detail.id() == null) {
            throw new IllegalArgumentException("Processing task detail is required.");
        }
        String uriPrefix = "rs://output/" + subject.userId() + "/";
        List<ArchiveEntry> entries = new ArrayList<>();
        for (TaskArtifactView artifact : detail.artifacts()) {
            String value = artifact.uri().value();
            if (!value.startsWith(uriPrefix)) {
                throw new IllegalArgumentException("Artifact URI is not owned by the current user.");
            }
            String[] segments = value.substring(uriPrefix.length()).split("/", 2);
            if (segments.length != 2 || !segments[0].matches("[A-Za-z0-9][A-Za-z0-9-]*")
                || segments[1].isBlank() || segments[1].contains("\\")
                || Path.of(segments[1]).isAbsolute() || Path.of(segments[1]).normalize().startsWith("..")) {
                throw new IllegalArgumentException("Artifact URI is invalid.");
            }
            Path file = findArtifact(detail, artifact, segments[1]);
            if (file == null) {
                throw new IOException("Registered artifact file is unavailable.");
            }
            entries.add(new ArchiveEntry(archiveName(segments[1], entries), file));
        }
        if (entries.isEmpty()) {
            throw new IOException("Processing task has no downloadable artifacts.");
        }
        Path archive = cachedArchive(detail, entries);
        response.setContentType("application/zip");
        String fileName = downloadFileName == null || downloadFileName.isBlank()
            ? "processing-result-" + detail.id().value() + ".zip"
            : downloadFileName;
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setBufferSize(RESPONSE_BUFFER_SIZE);
        if (archive != null) {
            response.setContentLengthLong(Files.size(archive));
            response.flushBuffer();
            Files.copy(archive, response.getOutputStream());
            return;
        }

        response.flushBuffer();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            // COG, PNG and Parquet artifacts are already compressed. Level zero avoids
            // minutes of redundant CPU work while retaining a standard streaming ZIP.
            zip.setLevel(Deflater.NO_COMPRESSION);
            for (ArchiveEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                Files.copy(entry.file(), zip);
                zip.closeEntry();
                zip.flush();
            }
        }
    }

    private Path cachedArchive(TaskDetailView detail, List<ArchiveEntry> entries) throws IOException {
        Path archive = workRoot.resolve("archives")
            .resolve(Long.toString(detail.id().value()))
            .resolve("processing-result.zip")
            .normalize();
        if (!archive.startsWith(workRoot) || !Files.isRegularFile(archive) || Files.size(archive) == 0) {
            return null;
        }
        long archiveModified = Files.getLastModifiedTime(archive).toMillis();
        for (ArchiveEntry entry : entries) {
            if (Files.getLastModifiedTime(entry.file()).toMillis() > archiveModified) {
                return null;
            }
        }
        return archive;
    }

    private Path findArtifact(TaskDetailView detail, TaskArtifactView artifact, String relative) throws IOException {
        String executionId = detail.stages().stream()
            .filter(stage -> stage.id().equals(artifact.stageId()))
            .map(stage -> stage.executionId())
            .filter(value -> value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
            .findFirst()
            .orElse(null);
        if (executionId == null) {
            return null;
        }
        Path output = workRoot.resolve(executionId).resolve("output").toAbsolutePath().normalize();
        Path file = output.resolve(relative).normalize();
        if (file.startsWith(output) && Files.isRegularFile(file)
            && checksum(file).equalsIgnoreCase(artifact.checksum())) {
            return file;
        }
        return null;
    }

    private static String archiveName(String relative, List<ArchiveEntry> entries) {
        String base = Path.of(relative).getFileName().toString();
        Set<String> names = new HashSet<>();
        entries.forEach(entry -> names.add(entry.name()));
        if (!names.contains(base)) {
            return base;
        }
        int index = 2;
        String candidate;
        do {
            candidate = index++ + "-" + base;
        } while (names.contains(candidate));
        return candidate;
    }

    private static String checksum(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream source = Files.newInputStream(file)) {
                byte[] buffer = new byte[1024 * 1024];
                for (int count; (count = source.read(buffer)) >= 0;) {
                    if (count > 0) {
                        digest.update(buffer, 0, count);
                    }
                }
            }
            return "sha256:" + hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private record ArchiveEntry(String name, Path file) {
    }
}
