package com.htht.config.multisource.archive;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multisource.archive-upload")
public class ArchiveUploadProperties {
    private long maxSizeBytes = 20L * 1024 * 1024 * 1024;
    private long maxEntryCount = 100_000;
    private long maxExpandedSizeBytes = 40L * 1024 * 1024 * 1024;
    private int workerThreads = 2;
    private int maxConcurrentParses = 1;
    private long safetyReserveBytes = 20L * 1024 * 1024 * 1024;
    private long staleSessionHours = 24;
    private long cleanupIntervalMs = 60 * 60 * 1000L;
    private String rootDirectory;
    public long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(long v) { maxSizeBytes = v; }
    public long getMaxEntryCount() { return maxEntryCount; }
    public void setMaxEntryCount(long v) { maxEntryCount = v; }
    public long getMaxExpandedSizeBytes() { return maxExpandedSizeBytes; }
    public void setMaxExpandedSizeBytes(long v) { maxExpandedSizeBytes = v; }
    public int getWorkerThreads() { return workerThreads; }
    public void setWorkerThreads(int v) { workerThreads = v; }
    public int getMaxConcurrentParses() { return maxConcurrentParses; }
    public void setMaxConcurrentParses(int v) { maxConcurrentParses = v; }
    public long getSafetyReserveBytes() { return safetyReserveBytes; }
    public void setSafetyReserveBytes(long v) { safetyReserveBytes = v; }
    public long getStaleSessionHours() { return staleSessionHours; }
    public void setStaleSessionHours(long v) { staleSessionHours = v; }
    public long getCleanupIntervalMs() { return cleanupIntervalMs; }
    public void setCleanupIntervalMs(long v) { cleanupIntervalMs = v; }
    public String getRootDirectory() { return rootDirectory; }
    public void setRootDirectory(String v) { rootDirectory = v; }
}
