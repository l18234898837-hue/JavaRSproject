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
    private final Path artifactRoot;

    public LocalWorkerResultArchiveService(MultiSourceWorkerProperties properties) {
        this.workRoot = properties.toLocalProcessProperties().workRoot().toAbsolutePath().normalize();
        this.artifactRoot = properties.getArtifactRoot() == null
            ? this.workRoot.resolve("artifacts") : properties.requireArtifactRoot().toAbsolutePath().normalize();
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
            String relative = relativeFor(subject, artifact);
            Path file = findArtifact(detail, artifact, relative);
            if (file == null) {
                throw new IOException("Registered artifact file is unavailable.");
            }
            entries.add(new ArchiveEntry(archiveName(relative, entries), file));
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

    public void writeArtifact(TaskDetailView detail, AccessSubject subject, String type,
                              HttpServletResponse response) throws IOException {
        writeArtifact(detail, subject, type, response, false);
    }

    public void writeArtifactPreview(TaskDetailView detail, AccessSubject subject, String type,
                                     HttpServletResponse response) throws IOException {
        writeArtifact(detail, subject, type, response, true);
    }

    private void writeArtifact(TaskDetailView detail, AccessSubject subject, String type,
                               HttpServletResponse response, boolean inline) throws IOException {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Artifact type is required.");
        }
        TaskArtifactView artifact = detail.artifacts().stream().filter(item -> type.equals(item.type()))
            .reduce((first, second) -> {
                throw new IllegalArgumentException("Artifact type is ambiguous for this processing task.");
            }).orElseThrow(() -> new java.util.NoSuchElementException("Processing artifact not found."));
        String relative = relativeFor(subject, artifact);
        Path file = findArtifact(detail, artifact, relative);
        if (file == null) {
            throw new IOException("Registered artifact file is unavailable.");
        }
        response.setContentType(mediaType(relative));
        response.setContentLengthLong(Files.size(file));
        response.setHeader("Content-Disposition", (inline ? "inline" : "attachment")
            + "; filename=\"" + Path.of(relative).getFileName() + "\"");
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setBufferSize(RESPONSE_BUFFER_SIZE);
        response.flushBuffer();
        Files.copy(file, response.getOutputStream());
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
        Path promoted = promotedArtifact(detail, artifact, relative);
        if (promoted != null) {
            return promoted;
        }
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

    private Path promotedArtifact(TaskDetailView detail, TaskArtifactView artifact, String relative) throws IOException {
        String storageKey = artifact.storageKey();
        if (storageKey == null || storageKey.isBlank() || storageKey.startsWith("legacy/")) {
            storageKey = inferredStorageKey(detail, artifact, relative);
        }
        if (!safeRelative(storageKey)) {
            return null;
        }
        Path file = artifactRoot.resolve(storageKey).normalize();
        if (file.startsWith(artifactRoot) && Files.isRegularFile(file)
            && checksum(file).equalsIgnoreCase(artifact.checksum())) {
            return file;
        }
        return null;
    }

    private static String inferredStorageKey(TaskDetailView detail, TaskArtifactView artifact, String relative) {
        var stage = detail.stages().stream().filter(item -> item.id().equals(artifact.stageId())).findFirst();
        if (stage.isEmpty()) {
            return null;
        }
        if ("FINAL".equals(artifact.retention())) {
            return detail.id().value() + "/output/" + relative;
        }
        return detail.id().value() + "/work/" + stage.get().stageKey() + "/" + stage.get().attempt() + "/" + relative;
    }

    private static String relativeFor(AccessSubject subject, TaskArtifactView artifact) {
        String uriPrefix = "rs://output/" + subject.userId() + "/";
        String value = artifact.uri().value();
        if (!value.startsWith(uriPrefix)) {
            throw new IllegalArgumentException("Artifact URI is not owned by the current user.");
        }
        String[] segments = value.substring(uriPrefix.length()).split("/", 2);
        if (segments.length != 2 || !segments[0].matches("[A-Za-z0-9][A-Za-z0-9-]*") || !safeRelative(segments[1])) {
            throw new IllegalArgumentException("Artifact URI is invalid.");
        }
        return segments[1];
    }

    private static boolean safeRelative(String value) {
        return value != null && !value.isBlank() && value.indexOf('\\') < 0 && !Path.of(value).isAbsolute()
            && !Path.of(value).normalize().startsWith("..");
    }

    private static String mediaType(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".geojson")) return "application/geo+json";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".txt") || lower.endsWith(".log")) return "text/plain";
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) return "image/tiff";
        return "application/octet-stream";
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
