package com.htht.multisource.domain.model;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class ProductCandidateMetadataTest {

    @Test
    void preservesObjectMetadataSnapshot() {
        ProductCandidate candidate = new ProductCandidate(
            "gf6-1420733151", new ProductProfileKey("GF-6-WFV-BROWSE", "v1"),
            ProductDataShape.RASTER_GRID, ProductSupportStatus.SUPPORTED, List.of(asset()),
            "{\"cloudPercent\":27}", 0.98
        );

        assertThat(candidate.metadataSnapshot()).isEqualTo("{\"cloudPercent\":27}");
    }

    private static InputAsset asset() {
        return new InputAsset("BROWSE_IMAGE", 0, new AssetReference("LOCAL_FILE", "gf6-1420733151.jpg"),
            new StorageUri("rs://input/gf6-1420733151.jpg"), "sha256:browse", true);
    }
}
