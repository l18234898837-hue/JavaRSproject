package com.htht.web.controller.multisource.dto;

import java.time.Instant;

public record ResultDownloadTicketResponse(String ticket, Instant expiresAt) {
}
