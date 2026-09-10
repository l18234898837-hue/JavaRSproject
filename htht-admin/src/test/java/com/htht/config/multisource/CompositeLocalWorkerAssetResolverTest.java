package com.htht.config.multisource;

import com.htht.multisource.application.port.out.model.ExecutionGrantRequest;
import com.htht.multisource.domain.model.ExecutionId;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.StageId;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.system.service.ISysFileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
    }
}
