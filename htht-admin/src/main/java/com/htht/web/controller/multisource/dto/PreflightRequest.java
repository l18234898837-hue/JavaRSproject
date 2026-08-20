package com.htht.web.controller.multisource.dto;

import java.util.List;
import java.util.Objects;

public record PreflightRequest(List<AssetRequest> assets) {

    public PreflightRequest {
        assets = List.copyOf(Objects.requireNonNull(assets, "assets"));
        if (assets.isEmpty()) {
            throw new IllegalArgumentException("assets must not be empty");
        }
    }
}
