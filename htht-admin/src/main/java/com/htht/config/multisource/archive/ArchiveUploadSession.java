package com.htht.config.multisource.archive;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class ArchiveUploadSession {
    final String id;
    final long subjectId;
    final String fileName;
    final long fileSize;
    final int chunkSize;
    final int totalChunks;
    final String templateId;
    final Path directory;
    final boolean[] chunks;
    final AtomicBoolean cancelRequested = new AtomicBoolean();
    volatile ArchiveUploadStatus status = ArchiveUploadStatus.UPLOADING;
    volatile int uploadedChunks;
    volatile int processedFiles;
    volatile Integer totalFiles;
    volatile String currentFile;
    volatile String error;
    final List<ArchiveUploadProgress.MatchedFile> matchedFiles = new ArrayList<>();
    final List<String> matchedSlots = new ArrayList<>();
    final List<String> missingSlots = new ArrayList<>();
    ArchiveUploadSession(String id, long subjectId, String fileName, long fileSize, int chunkSize,
                         int totalChunks, String templateId, Path directory) {
        this.id = id; this.subjectId = subjectId; this.fileName = fileName; this.fileSize = fileSize;
        this.chunkSize = chunkSize; this.totalChunks = totalChunks; this.templateId = templateId;
        this.directory = directory; this.chunks = new boolean[totalChunks];
    }
    synchronized ArchiveUploadProgress snapshot() {
        return new ArchiveUploadProgress(id, status, uploadedChunks, totalChunks, processedFiles,
            totalFiles, currentFile, List.copyOf(matchedFiles), List.copyOf(matchedSlots),
            List.copyOf(missingSlots), error);
    }
}
