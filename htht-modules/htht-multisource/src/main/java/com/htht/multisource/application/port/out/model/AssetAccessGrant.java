package com.htht.multisource.application.port.out.model;

import java.time.Instant;

public record AssetAccessGrant(String grantReference, Instant expiresAt) {
}
