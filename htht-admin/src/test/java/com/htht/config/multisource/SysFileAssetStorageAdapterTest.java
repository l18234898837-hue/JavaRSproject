package com.htht.config.multisource;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
public class SysFileAssetStorageAdapterTest {

    @TempDir
    Path uploadRoot;

    @Test
    void implementsAssetStorageContract() {
        assertThat(AssetStorage.class.isAssignableFrom(SysFileAssetStorageAdapter.class)).isTrue();
    }

    @Test
    void mapsOnlyServerManagedFilesToStableInputSnapshots() throws Exception {
        Path directory = Files.createDirectories(uploadRoot.resolve("hdf"));
        Path file = Files.writeString(directory.resolve("8b083cd7-5181-4af2-94d2-91357d6d8938.hdf"), "ocean-data");
        ISysFileService files = mock(ISysFileService.class);
        when(files.getById(42L)).thenReturn(sysFile(42L, file,
            "FY4B-_AGRI--_N_DISK_1050E_L1-_GEO-_MULT_NOM_20260701000000_20260701001459_4000M_V0001",
            "HDF"));
        SysFileAssetStorageAdapter adapter = new SysFileAssetStorageAdapter(files, uploadRoot);

        var asset = adapter.toInputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), true);
        var grant = adapter.grantRead(new AccessSubject(7L), java.util.List.of(asset));

        assertThat(asset.storageUriSnapshot().value()).isEqualTo("rs://sys-file/42");
        assertThat(asset.checksum()).isEqualTo("sha256:a16a137dd0219234e90ff5848180727180554414f2d8dbdcd213cb69f951d570");
        assertThat(adapter.resolve(grant.grantReference(), new StorageUri("rs://sys-file/42"))).isEqualTo(file.toRealPath());
        assertThat(adapter.originalFileName(grant.grantReference(), new StorageUri("rs://sys-file/42"), file.toRealPath()))
            .isEqualTo("FY4B-_AGRI--_N_DISK_1050E_L1-_GEO-_MULT_NOM_20260701000000_20260701001459_4000M_V0001.HDF");
    }

    @Test
    void recalculatesChecksumWhenTheManagedFileChanges() throws Exception {
        Path file = Files.writeString(Files.createDirectories(uploadRoot.resolve("tiff")).resolve("input.tiff"), "first");
        ISysFileService files = mock(ISysFileService.class);
        when(files.getById(42L)).thenReturn(sysFile(42L, file));
        SysFileAssetStorageAdapter adapter = new SysFileAssetStorageAdapter(files, uploadRoot);

        String first = adapter.toInputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), true).checksum();
        Files.writeString(file, "second");
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 1_000));
        String second = adapter.toInputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), true).checksum();

        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void rejectsNonSysFileReferencesAndPathsOutsideUploadRoot(@TempDir Path outsideRoot) throws Exception {
        Path outside = Files.writeString(outsideRoot.resolve("outside.hdf"), "outside");
        ISysFileService files = mock(ISysFileService.class);
        when(files.getById(43L)).thenReturn(sysFile(43L, outside));
        SysFileAssetStorageAdapter adapter = new SysFileAssetStorageAdapter(files, uploadRoot);

        assertThatIllegalArgumentException().isThrownBy(
            () -> adapter.toInputAsset("MAIN", 0, new AssetReference("LOCAL_FILE", "43"), true)
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> adapter.toInputAsset("MAIN", 0, new AssetReference("SYS_FILE", "43"), true)
        );
    }

    @Test
    void resolvesOnlyStorageUrisIncludedInAnActiveGrant() throws Exception {
        Path first = Files.writeString(Files.createDirectories(uploadRoot.resolve("hdf")).resolve("first.hdf"), "first");
        Path second = Files.writeString(uploadRoot.resolve("hdf/second.hdf"), "second");
        ISysFileService files = mock(ISysFileService.class);
        when(files.getById(42L)).thenReturn(sysFile(42L, first));
        when(files.getById(43L)).thenReturn(sysFile(43L, second));
        SysFileAssetStorageAdapter adapter = new SysFileAssetStorageAdapter(files, uploadRoot);
        InputAsset granted = adapter.toInputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), true);
        var grant = adapter.grantRead(new AccessSubject(7L), java.util.List.of(granted));

        assertThat(adapter.resolve(grant.grantReference(), granted.storageUriSnapshot())).isEqualTo(first.toRealPath());
        assertThatIllegalArgumentException().isThrownBy(
            () -> adapter.resolve(grant.grantReference(), new StorageUri("rs://sys-file/43"))
        );
    }

    @Test
    void executionGrantBindsTaskStageExecutionAndAttempt() throws Exception {
        Path file = Files.writeString(Files.createDirectories(uploadRoot.resolve("hdf")).resolve("input.hdf"), "input");
        ISysFileService files = mock(ISysFileService.class);
        when(files.getById(42L)).thenReturn(sysFile(42L, file));
        SysFileAssetStorageAdapter adapter = new SysFileAssetStorageAdapter(files, uploadRoot);
        InputAsset input = adapter.toInputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), true);

        var grant = adapter.grant(new ExecutionGrantRequest(
            TaskId.of(101L), new StageId(201L), new ExecutionId("exec-01"), 2,
            java.util.List.of(input), new StorageUri("rs://output/7/task-01")
        ));

        assertThat(grant.taskId()).isEqualTo(TaskId.of(101L));
        assertThat(grant.stageId()).isEqualTo(new StageId(201L));
        assertThat(grant.executionId()).isEqualTo(new ExecutionId("exec-01"));
        assertThat(grant.attempt()).isEqualTo(2);
        assertThat(adapter.resolve(grant.grantReference(), input.storageUriSnapshot())).isEqualTo(file.toRealPath());
    }

    private SysFile sysFile(long id, Path path) {
        String name = path.getFileName().toString();
        int separator = name.lastIndexOf('.');
        return sysFile(id, path, separator > 0 ? name.substring(0, separator) : name,
            separator > 0 ? name.substring(separator + 1) : null);
    }

    private SysFile sysFile(long id, Path path, String fileName, String extension) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setFileName(fileName);
        file.setExtension(extension);
        file.setFilePath(path.toString());
        return file;
    }
}
