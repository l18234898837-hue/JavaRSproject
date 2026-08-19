package com.htht.multisource.application.usecase;

import com.htht.multisource.application.usecase.model.BatchCreateCommand;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StorageUri;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.candidate;
import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class CreateProcessingTaskUseCaseTest {

    @Test
    void batchCommandCopiesSelectedCandidates() {
        ProductCandidate candidate = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
        List<ProductCandidate> selected = new ArrayList<>(List.of(candidate));

        BatchCreateCommand command = new BatchCreateCommand(
            "batch-01", subject(), selected, "{\"mode\":\"standard\"}", new StorageUri("rs://output/batch-01")
        );
        selected.clear();

        assertThat(command.candidates()).containsExactly(candidate);
        assertThatThrownBy(() -> command.candidates().add(candidate))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
