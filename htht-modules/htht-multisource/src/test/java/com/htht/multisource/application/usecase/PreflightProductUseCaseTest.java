package com.htht.multisource.application.usecase;

import com.htht.multisource.application.port.out.AssetStorage;
import com.htht.multisource.application.port.out.model.AssetAccessGrant;
import com.htht.multisource.application.port.out.model.AssetMetadata;
import com.htht.multisource.application.port.out.model.ExecutionGrant;
import com.htht.multisource.application.port.out.model.InspectionResponse;
import com.htht.multisource.application.usecase.model.PreflightResult;
import com.htht.multisource.application.usecase.service.PreflightProductService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.multisource.domain.model.AssetReference;
import com.htht.multisource.domain.model.InputAsset;
import com.htht.multisource.domain.model.ProductCandidate;
import com.htht.multisource.domain.model.ProductSupportStatus;
import com.htht.multisource.domain.model.StorageUri;
import com.htht.multisource.domain.model.TaskId;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.candidate;
import static com.htht.multisource.application.usecase.ApplicationUseCaseTestFixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class PreflightProductUseCaseTest {

    @Test
    void preflightReturnsEveryCandidateFromInspector() {
        ProductCandidate first = candidate("fy3h-granule-01", ProductSupportStatus.SUPPORTED);
        ProductCandidate second = candidate("fy3h-granule-02", ProductSupportStatus.CANDIDATE);
        PreflightProductService service = new PreflightProductService(
            request -> new InspectionResponse("rules-2026.08", List.of(first, second)),
            new FakeAssetStorage()
        );

        PreflightResult result = service.preflight(subject(), List.of(first.files().get(0)));

        assertThat(result.rulePackageVersion()).isEqualTo("rules-2026.08");
        assertThat(result.candidates()).containsExactly(first, second);
        assertThatThrownBy(() -> result.candidates().add(first))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static final class FakeAssetStorage implements AssetStorage {

        @Override
        public AssetAccessGrant grantRead(AccessSubject subject, List<InputAsset> inputs) {
            return new AssetAccessGrant("read-1", Instant.parse("2026-08-20T00:00:00Z"));
        }

        @Override
        public ExecutionGrant grant(TaskId taskId, List<InputAsset> inputs, StorageUri outputUri) {
            throw new UnsupportedOperationException("Execution grants are outside preflight tests.");
        }

        @Override
        public AssetMetadata resolve(AccessSubject subject, AssetReference reference) {
            throw new UnsupportedOperationException("Asset resolution is outside preflight tests.");
        }
    }
}
