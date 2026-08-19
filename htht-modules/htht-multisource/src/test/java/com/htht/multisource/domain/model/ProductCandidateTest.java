package com.htht.multisource.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class ProductCandidateTest {

    private final InputAsset mainFile = new InputAsset(
        "MAIN",
        0,
        new AssetReference("LOCAL_FILE", "fy3h-mersi-01.hdf"),
        "sha256:main",
        true
    );
    private final InputAsset metadataFile = new InputAsset(
        "METADATA",
        1,
        new AssetReference("LOCAL_FILE", "fy3h-mersi-01.xml"),
        "sha256:metadata",
        false
    );

    @Test
    void candidateListMustBeCopiedAndVisible() {
        List<InputAsset> files = new ArrayList<>();
        files.add(mainFile);
        files.add(metadataFile);

        ProductCandidate candidate = new ProductCandidate(
            "fy3h-mersi-granule-01",
            new ProductProfileKey("FY-3H-MERSI", "v1"),
            ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.CANDIDATE,
            files,
            0.87
        );
        files.clear();

        assertThat(candidate.files()).containsExactly(mainFile, metadataFile);
        assertThatThrownBy(() -> candidate.files().add(mainFile))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void candidateMustHaveFilesAndValidConfidence() {
        ProductProfileKey profileKey = new ProductProfileKey("FY-3H-MERSI", "v1");

        assertThatThrownBy(() -> new ProductCandidate(
            "fy3h-mersi-granule-01",
            profileKey,
            ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.CANDIDATE,
            List.of(),
            0.87
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ProductCandidate(
            "fy3h-mersi-granule-01",
            profileKey,
            ProductDataShape.SWATH_RASTER,
            ProductSupportStatus.CANDIDATE,
            List.of(mainFile),
            1.01
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void candidateCanRemainUnknownBeforeProfileVerification() {
        ProductCandidate candidate = new ProductCandidate(
            "unknown-package-01",
            new ProductProfileKey("UNKNOWN", "unverified"),
            ProductDataShape.DOCUMENT,
            ProductSupportStatus.UNKNOWN_PRODUCT,
            List.of(mainFile),
            0.12
        );

        assertThat(candidate.supportStatus()).isEqualTo(ProductSupportStatus.UNKNOWN_PRODUCT);
    }
}
