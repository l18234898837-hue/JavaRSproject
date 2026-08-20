package com.htht.web.controller.multisource;

import com.htht.web.controller.multisource.dto.AssetRequest;
import com.htht.web.controller.multisource.dto.CreateBatchRequest;
import com.htht.web.controller.multisource.dto.CreateTaskRequest;
import com.htht.web.controller.multisource.dto.PreflightRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

@Tag("dev")
public class MultisourceDtoValidationTest {

    @Test
    void acceptsOnlyPositiveSysFileAssetReferences() {
        AssetRequest request = new AssetRequest("SYS_FILE", 42L, "MAIN", 0, true);

        assertThat(request.sourceType()).isEqualTo("SYS_FILE");
        assertThat(request.sourceId()).isEqualTo(42L);
        assertThat(request.role()).isEqualTo("MAIN");
        assertThat(request.ordinal()).isZero();
        assertThat(request.required()).isTrue();
    }

    @Test
    void rejectsUnsupportedOrIncompleteAssetReferences() {
        assertThatIllegalArgumentException().isThrownBy(
            () -> new AssetRequest("LOCAL_FILE", 42L, "MAIN", 0, true)
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> new AssetRequest("SYS_FILE", 0L, "MAIN", 0, true)
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> new AssetRequest("SYS_FILE", 42L, " ", 0, true)
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> new AssetRequest("SYS_FILE", 42L, "MAIN", -1, true)
        );
    }

    @Test
    void preservesPreflightInputOrderAndTaskSelection() {
        AssetRequest auxiliary = new AssetRequest("SYS_FILE", 43L, "AUXILIARY", 1, false);
        AssetRequest main = new AssetRequest("SYS_FILE", 42L, "MAIN", 0, true);

        PreflightRequest preflight = new PreflightRequest(List.of(auxiliary, main));
        CreateTaskRequest task = new CreateTaskRequest("ocean task", "token-1", "c0", "{\"threshold\":0.7}");

        assertThat(preflight.assets()).containsExactly(auxiliary, main);
        assertThat(task.preflightToken()).isEqualTo("token-1");
        assertThat(task.candidateId()).isEqualTo("c0");
    }

    @Test
    void rejectsIncompletePreflightAndCreationRequests() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PreflightRequest(List.of()));
        assertThatIllegalArgumentException().isThrownBy(
            () -> new CreateTaskRequest(" ", "token-1", "c0", "{}")
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> new CreateTaskRequest("task", " ", "c0", "{}")
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> new CreateTaskRequest("task", "token-1", " ", "{}")
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> new CreateTaskRequest("task", "token-1", "c0", " ")
        );
        assertThatIllegalArgumentException().isThrownBy(
            () -> new CreateBatchRequest("batch", "token-1", List.of(), "{}")
        );
    }
}
