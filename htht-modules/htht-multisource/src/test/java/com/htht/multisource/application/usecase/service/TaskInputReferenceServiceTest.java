package com.htht.multisource.application.usecase.service;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.ManagedInputFileRepository;
import com.htht.multisource.application.port.out.TaskInputReferenceRepository;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.AssetMetadata;
import com.htht.multisource.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TaskInputReferenceServiceTest {
    @Test void registersSysFileOnceAndSharesManagedFile() {
        Fakes f = new Fakes(); TaskInputReferenceService s = new TaskInputReferenceService(f.files, f.refs, f.storage);
        ProcessingTask t1 = task(1); ProcessingTask t2 = task(2);
        s.register(t1, "default"); s.register(t1, "default"); s.register(t2, "default");
        assertThat(f.files.calls).isEqualTo(3); assertThat(f.refs.rows).hasSize(2);
        assertThat(f.refs.rows).allMatch(r -> r.managedId == 9);
    }

    @Test void rejectsSnapshotChecksumMismatch() {
        Fakes f = new Fakes(); f.storage.checksum = "sha256:other";
        assertThatThrownBy(() -> new TaskInputReferenceService(f.files, f.refs, f.storage).register(task(1), "default"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("checksum");
    }

    private static ProcessingTask task(long id) {
        InputAsset input = new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), new StorageUri("rs://sys-file/42"), "sha256:ok", true);
        ProductProfileKey key = new ProductProfileKey("P", "1");
        ProductCandidate c = new ProductCandidate("pkg" + id, key, ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED, List.of(input), 1.0);
        ProcessingStage stage = new ProcessingStage("S", "S", List.of(), List.of(), List.of(), "{}");
        return new ProcessingTask(TaskId.of(id), "t" + id, new AccessSubject(7), TaskLifecycleStatus.CREATED, QualityStatus.NOT_CHECKED, "{}", new StorageUri("rs://out"),
            new ProcessingPlan("1", "p", List.of(stage)), new ProcessingBatch(id, "b", ProcessingBatchStatus.CREATED, List.of(c)), List.of(c));
    }

    private static final class Fakes {
        final FakeFiles files = new FakeFiles(); final FakeRefs refs = new FakeRefs(); final FakeStorage storage = new FakeStorage();
    }
    private static final class FakeFiles implements ManagedInputFileRepository { int calls; public long findOrRegister(String w,long s,String p,String c,long z){calls++; return 9;} }
    private static final class FakeRefs implements TaskInputReferenceRepository { List<Row> rows = new ArrayList<>(); public void findOrRegister(TaskId t,long m,String st,String si,String c){ if(rows.stream().noneMatch(r->r.task.equals(t))) rows.add(new Row(t,m)); } public List<Long> findActiveReferences(long m){return rows.stream().map(r->r.managedId).toList();} public void releaseForTask(TaskId t,long o,Instant n){} }
    private record Row(TaskId task,long managedId) {}
    private static final class FakeStorage implements AssetStorage { String checksum="sha256:ok"; public ManagedInputFileMetadata managedInputFile(AccessSubject s, AssetReference r){return new ManagedInputFileMetadata("/upload/42",2,checksum);} public AssetAccessGrant grantRead(AccessSubject s,List<InputAsset> i){throw new UnsupportedOperationException();} public ExecutionGrant grant(TaskId t,List<InputAsset> i,StorageUri o){throw new UnsupportedOperationException();} public AssetMetadata resolve(AccessSubject s,AssetReference r){throw new UnsupportedOperationException();} }
}
