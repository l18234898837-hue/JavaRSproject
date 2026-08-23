package com.htht.web.controller.multisource;

import com.htht.config.multisource.ServerOutputUriAllocator;
import com.htht.multisource.application.port.out.PreflightSessionStore;
import com.htht.multisource.application.usecase.CreateProcessingBatchUseCase;
import com.htht.multisource.application.usecase.model.BatchCreationResult;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProcessingBatch;
import com.htht.multisource.domain.model.ProcessingBatchStatus;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductDataShape;
import com.htht.multisource.domain.model.ProductProfileKey;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import com.htht.web.controller.multisource.dto.CreateBatchRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
public class ProcessingBatchControllerTest {

    @Test
    void createsBatchOnlyFromTheServerSideTokenSelection() {
        ProductCandidate first = candidate("first");
        ProductCandidate second = candidate("second");
        PreflightSessionStore sessions = mock(PreflightSessionStore.class);
        PreflightSessionStore.SelectionLease lease = mock(PreflightSessionStore.SelectionLease.class);
        when(sessions.acquire(any(), any(), any())).thenReturn(lease);
        when(lease.candidates()).thenReturn(List.of(first, second));
        CreateProcessingBatchUseCase create = mock(CreateProcessingBatchUseCase.class);
        when(create.create(any())).thenReturn(new BatchCreationResult(
            new ProcessingBatch(33L, "batch", ProcessingBatchStatus.CREATED, List.of(first, second)),
            List.of(TaskId.of(101L), TaskId.of(102L))
        ));
        ProcessingBatchController controller = new ProcessingBatchController(
            () -> new AccessSubject(7L), sessions, create, new ServerOutputUriAllocator(() -> "request-1")
        );

        var response = controller.create(new CreateBatchRequest("batch", "token-1", List.of("c0", "c1"), "{}"));

        assertThat(response.getStatusCodeValue()).isEqualTo(201);
        assertThat(response.getBody().getData().batchId()).isEqualTo(33L);
        assertThat(response.getBody().getData().taskIds()).containsExactly(101L, 102L);
        verify(lease).commit();
    }

    private ProductCandidate candidate(String id) {
        InputAsset asset = new InputAsset("MAIN", 0, new AssetReference("SYS_FILE", "42"), new StorageUri("rs://sys-file/42"), "sha256:test", true);
        return new ProductCandidate(id, new ProductProfileKey("FY-3H-MERSI", "v1"), ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.SUPPORTED, List.of(asset), 0.9);
    }
}
