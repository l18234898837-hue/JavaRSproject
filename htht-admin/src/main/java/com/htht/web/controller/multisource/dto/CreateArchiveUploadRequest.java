package com.htht.web.controller.multisource.dto;
public record CreateArchiveUploadRequest(String fileName, long fileSize, int chunkSize, int totalChunks, String templateId) {}
