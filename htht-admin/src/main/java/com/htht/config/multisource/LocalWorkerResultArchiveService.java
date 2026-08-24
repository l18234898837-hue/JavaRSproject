package com.htht.config.multisource;

import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public final class LocalWorkerResultArchiveService {
    private final Path workRoot;

    public LocalWorkerResultArchiveService(MultiSourceWorkerProperties properties) {
        this.workRoot = properties.toLocalProcessProperties().workRoot().toAbsolutePath().normalize();
    }

    public void write(TaskDetailView detail, AccessSubject subject, HttpServletResponse response) throws IOException {
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
            Path file = findArtifact(segments[1], artifact.checksum());
            if (file == null) {
                throw new IOException("Registered artifact file is unavailable.");
            }
            entries.add(new ArchiveEntry(archiveName(segments[1], entries), file));
        }
        if (entries.isEmpty()) {
            throw new IOException("Processing task has no downloadable artifacts.");
        }
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=processing-result.zip");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            for (ArchiveEntry entry : entries) {
                zip.putNextEntry(new ZipEntry(entry.name()));
                Files.copy(entry.file(), zip);
                zip.closeEntry();
            }
        }
    }

    private Path findArtifact(String relative, String expectedChecksum) throws IOException {
        if (!Files.isDirectory(workRoot)) {
            return null;
        }
        try (var children = Files.list(workRoot)) {
            for (Path execution : children.filter(Files::isDirectory).toList()) {
                Path output = execution.resolve("output").toAbsolutePath().normalize();
                Path file = output.resolve(relative).normalize();
                if (file.startsWith(output) && Files.isRegularFile(file)
                    && checksum(file).equalsIgnoreCase(expectedChecksum)) {
                    return file;
                }
            }
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
            return "sha256:" + hex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
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
