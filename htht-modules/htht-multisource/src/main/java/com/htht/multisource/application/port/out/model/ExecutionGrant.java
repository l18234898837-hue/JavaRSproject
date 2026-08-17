package com.htht.multisource.application.port.out.model;

import java.time.Instant;

public record ExecutionGrant(String grantReference, Instant expiresAt) {
}
