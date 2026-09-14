package com.htht.config.multisource;

import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class CompositeLocalWorkerAssetResolverTest {

    @TempDir
    Path root;

    @Test
    void resolvesAnAuthorizedLegacyWorkerOutputWithoutLeavingTheWorkerRoot() throws Exception {
        Path source = Files.writeString(
            Files.createDirectories(root.resolve("exec-01/output/decoded")).resolve("czi.npz"),
            "decoded"
        );
        StorageUri uri = new StorageUri("rs://worker-output/exec-01/decoded/czi.npz");
        InputAsset input = new InputAsset("decoded", 0,
            new AssetReference("TASK_ARTIFACT", "rs://output/7/task-01/decoded/czi.npz"),
            uri, "sha256:decoded", true);
        SysFileAssetStorageAdapter sysFiles = new SysFileAssetStorageAdapter(mock(ISysFileService.class), root);
        CompositeLocalWorkerAssetResolver resolver = new CompositeLocalWorkerAssetResolver(sysFiles, root);
        var grant = resolver.grant(new ExecutionGrantRequest(TaskId.of(101L), new StageId(201L),
            new ExecutionId("next-exec"), 1, List.of(input), new StorageUri("rs://output/7/task-01")));

        assertThat(resolver.resolve(grant.grantReference(), uri)).isEqualTo(source.toRealPath());
        assertThat(resolver.originalFileName(grant.grantReference(), uri, source.toRealPath()))
            .isEqualTo("czi.npz");
    }

    @Test
    void preservesPersistedOriginalNameForAuthorizedSysFileInputs() throws Exception {
        Path physical = Files.writeString(
            Files.createDirectories(root.resolve("uploads")).resolve("0292c1c30a404c77914530cb998a1bca.HDF"),
            "hdf5"
        );
        ISysFileService fileService = mock(ISysFileService.class);
        SysFile file = new SysFile();
        file.setId(42L);
        file.setFilePath(physical.toString());
        file.setFileName("FY4B-_AGRI--_N_DISK_1050E_L1-_GEO-_MULT_NOM_20260701000000_20260701001459_4000M_V0001");
        file.setExtension("HDF");
        when(fileService.getById(42L)).thenReturn(file);

        SysFileAssetStorageAdapter sysFiles = new SysFileAssetStorageAdapter(fileService, root);
        CompositeLocalWorkerAssetResolver resolver = new CompositeLocalWorkerAssetResolver(sysFiles, root);
        StorageUri uri = new StorageUri("rs://sys-file/42");
        InputAsset input = new InputAsset("MAIN", 0,
            new AssetReference("SYS_FILE", "42"), uri, "sha256:hdf5", true);
        var grant = resolver.grant(new ExecutionGrantRequest(TaskId.of(101L), new StageId(201L),
            new ExecutionId("fy4b-exec"), 1, List.of(input), new StorageUri("rs://output/7/task-01")));

        Path resolved = resolver.resolve(grant.grantReference(), uri);

        assertThat(resolved).isEqualTo(physical.toRealPath());
        assertThat(resolver.originalFileName(grant.grantReference(), uri, resolved))
            .isEqualTo("FY4B-_AGRI--_N_DISK_1050E_L1-_GEO-_MULT_NOM_20260701000000_20260701001459_4000M_V0001.HDF");
    }

    @Test
    void preservesOriginalNamesForAllCommonSysFileFormats() throws Exception {
        ISysFileService fileService = mock(ISysFileService.class);
        Map<Long, String> names = Map.of(
            51L, "scene.tif",
            52L, "browse.png",
            53L, "metadata.json"
        );
        for (Map.Entry<Long, String> entry : names.entrySet()) {
            long id = entry.getKey();
            String originalName = entry.getValue();
            String physicalName = "uuid-" + id + "." + originalName.substring(originalName.lastIndexOf('.') + 1);
            Path physical = Files.writeString(
                Files.createDirectories(root.resolve("uploads")).resolve(physicalName),
                originalName
            );
            SysFile file = new SysFile();
            file.setId(id);
            file.setFilePath(physical.toString());
            file.setFileName(originalName.substring(0, originalName.lastIndexOf('.')));
            file.setExtension(originalName.substring(originalName.lastIndexOf('.') + 1));
            when(fileService.getById(id)).thenReturn(file);
        }

        SysFileAssetStorageAdapter sysFiles = new SysFileAssetStorageAdapter(fileService, root);
        CompositeLocalWorkerAssetResolver resolver = new CompositeLocalWorkerAssetResolver(sysFiles, root);

        for (Map.Entry<Long, String> entry : names.entrySet()) {
            long id = entry.getKey();
            StorageUri uri = new StorageUri("rs://sys-file/" + id);
            InputAsset input = new InputAsset("INPUT", (int) id,
                new AssetReference("SYS_FILE", Long.toString(id)), uri, "sha256:test", true);
            var grant = resolver.grant(new ExecutionGrantRequest(TaskId.of(101L), new StageId(201L),
                new ExecutionId("format-" + id), 1, List.of(input), new StorageUri("rs://output/7/task-01")));
            Path resolved = resolver.resolve(grant.grantReference(), uri);

            assertThat(resolver.originalFileName(grant.grantReference(), uri, resolved))
                .isEqualTo(entry.getValue());
        }
    }
}
