package com.htht.config.multisource.archive;

import java.util.List;

public record ArchiveUploadProgress(String uploadId, ArchiveUploadStatus status, int uploadedChunks,
                                    int totalChunks, int processedFiles, Integer totalFiles,
                                    String currentFile, List<MatchedFile> matchedFiles,
                                    List<String> matchedSlots, List<String> missingSlots, String error) {
    public record MatchedFile(String slotId, String fileName, String sysFileId) {}
}
