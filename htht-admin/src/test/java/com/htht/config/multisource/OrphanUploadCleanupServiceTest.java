package com.htht.config.multisource;

import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class OrphanUploadCleanupServiceTest {
    @TempDir Path temp;

    @Test void removesOnlyExpiredTransientUploads() throws Exception {
        Path archive = Files.createDirectories(temp.resolve(".multisource-archive"));
        Path recent = Files.writeString(temp.resolve("still.uploading"), "recent");
        Files.setLastModifiedTime(archive, FileTime.from(Instant.parse("2026-01-01T00:00:00Z")));
        Files.setLastModifiedTime(recent, FileTime.from(Instant.parse("2026-01-02T00:01:00Z")));
        var service = new OrphanUploadCleanupService(temp, new FilesRegistry(), path -> false, Duration.ofHours(24));
        assertThat(service.scanAndCleanup(Instant.parse("2026-01-03T00:00:00Z"))).isEqualTo(1);
        assertThat(archive).doesNotExist(); assertThat(recent).exists();
    }

    @Test void preservesRegisteredAndActiveUploads() throws Exception {
        Path registered = Files.writeString(temp.resolve("registered.part"), "keep");
        Path active = Files.writeString(temp.resolve("active.incomplete"), "keep");
        Instant old = Instant.parse("2026-01-01T00:00:00Z");
        Files.setLastModifiedTime(registered, FileTime.from(old)); Files.setLastModifiedTime(active, FileTime.from(old));
        var service = new OrphanUploadCleanupService(temp, new FilesRegistry(registered), active::equals, Duration.ofHours(24));
        assertThat(service.scanAndCleanup(Instant.parse("2026-01-03T00:00:00Z"))).isZero();
        assertThat(registered).exists(); assertThat(active).exists();
    }

    private static final class FilesRegistry implements ManagedInputFileRepository {
        private final Path registered;
        FilesRegistry() { this(null); } FilesRegistry(Path registered) { this.registered = registered; }
        @Override public long findOrRegister(String w, long s, String p, String c, long z) { return 0; }
        @Override public Optional<ManagedInputFile> findByPath(Path path) {
            return registered != null && registered.toAbsolutePath().normalize().equals(path) ? Optional.of(new ManagedInputFile(1, "default", 1, path, "a", 1, "MANAGED", 0)) : Optional.empty();
        }
    }
}
