package com.htht.config.multisource;

import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.domain.model.TaskId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class LocalManagedInputFilePurgeServiceTest {
    @TempDir Path temp;

    @Test void rejectsTraversalWithoutTouchingOtherFiles() throws Exception {
        Path root = Files.createDirectories(temp.resolve("uploads"));
        Path outside = Files.writeString(temp.resolve("outside.tif"), "keep");
        FakeFiles files = new FakeFiles();
        var service = new LocalManagedInputFilePurgeService(root, files, new References());
        var managed = file(1, outside);
        assertThatThrownBy(() -> service.purge(managed)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("escapes");
        assertThat(outside).exists();
    }

    @Test void protectsSharedInputAndDeletesUnreferencedInput() throws Exception {
        Path root = Files.createDirectories(temp.resolve("uploads"));
        Path input = Files.writeString(root.resolve("input.tif"), "source");
        FakeFiles files = new FakeFiles();
        References references = new References(); references.active = List.of(99L);
        var service = new LocalManagedInputFilePurgeService(root, files, references);
        assertThat(service.purge(file(1, input))).isEqualTo(com.htht.multisource.application.port.out.ManagedInputFilePurgePort.Result.PROTECTED);
        assertThat(input).exists();
        references.active = List.of();
        assertThat(service.purge(file(1, input))).isEqualTo(com.htht.multisource.application.port.out.ManagedInputFilePurgePort.Result.PURGED);
        assertThat(input).doesNotExist(); assertThat(files.purged).isTrue();
    }

    @Test void treatsMissingManagedFileAsIdempotentSuccess() throws Exception {
        Path root = Files.createDirectories(temp.resolve("uploads"));
        FakeFiles files = new FakeFiles();
        var service = new LocalManagedInputFilePurgeService(root, files, new References());
        assertThat(service.purge(file(1, root.resolve("gone.tif")))).isEqualTo(com.htht.multisource.application.port.out.ManagedInputFilePurgePort.Result.MISSING);
        assertThat(files.purged).isTrue();
    }

    private static ManagedInputFileRepository.ManagedInputFile file(long id, Path path) {
        return new ManagedInputFileRepository.ManagedInputFile(id, "default", id, path, "abc", 1, "MANAGED", 0);
    }
    private static final class FakeFiles implements ManagedInputFileRepository {
        boolean purged;
        @Override public long findOrRegister(String w, long s, String p, String c, long z) { return 0; }
        @Override public void markPurged(long id, long version) { purged = true; }
    }
    private static final class References implements TaskInputReferenceRepository {
        List<Long> active = List.of();
        @Override public void findOrRegister(TaskId t, long f, String st, String si, String c) { }
        @Override public List<Long> findActiveReferences(long id) { return active; }
        @Override public void releaseForTask(TaskId taskId, long userId, Instant now) { }
    }
}
