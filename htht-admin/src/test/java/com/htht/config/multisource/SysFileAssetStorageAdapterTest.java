package com.htht.config.multisource;

import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
public class SysFileAssetStorageAdapterTest {

    @TempDir
    Path uploadRoot;

    @Test
    void mapsOnlyServerManagedFilesToStableInputSnapshots() throws Exception {
        Path directory = Files.createDirectories(uploadRoot.resolve("hdf"));
        Path file = Files.writeString(directory.resolve("product.hdf"), "ocean-data");
        ISysFileService files = mock(ISysFileService.class);
        when(files.getById(42L)).thenReturn(sysFile(42L, file));
        SysFileAssetStorageAdapter adapter = new SysFileAssetStorageAdapter(files, uploadRoot);

        var asset = adapter.toInputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), true);
        var grant = adapter.grantRead(new AccessSubject(7L), java.util.List.of(asset));

        assertThat(asset.storageUriSnapshot().value()).isEqualTo("rs://sys-file/42");
        assertThat(asset.checksum()).isEqualTo("sha256:a16a137dd0219234e90ff5848180727180554414f2d8dbdcd213cb69f951d570");
        assertThat(adapter.resolve(grant.grantReference(), new StorageUri("rs://sys-file/42"))).isEqualTo(file.toRealPath());
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

    private SysFile sysFile(long id, Path path) {
        SysFile file = new SysFile();
        file.setId(id);
        file.setFilePath(path.toString());
        return file;
    }
}
