package com.htht.config.multisource.archive;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Scheduled;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.function.LongSupplier;

@Service
public class ArchiveUploadService {
    private final ArchiveUploadProperties properties;
    private final Path uploadRoot;
    private final Map<String, ArchiveUploadSession> sessions = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final ISysFileService sysFileService;
    private final Semaphore parsePermits;
    private final LongSupplier usableSpaceSupplier;

    @Autowired
    public ArchiveUploadService(ArchiveUploadProperties properties, @Value("${file.upload-path}") String uploadPath,
                                ISysFileService sysFileService) {
        this(properties, uploadPath, sysFileService, null);
    }

    ArchiveUploadService(ArchiveUploadProperties properties, String uploadPath,
                         ISysFileService sysFileService, LongSupplier usableSpaceSupplier) {
        this.properties = properties;
        this.sysFileService = sysFileService;
        Path fileUploadRoot = Path.of(uploadPath).toAbsolutePath().normalize();
        Path configuredRoot = Path.of(properties.getRootDirectory() == null || properties.getRootDirectory().isBlank()
            ? uploadPath + "/.multisource-archive" : properties.getRootDirectory()).toAbsolutePath().normalize();
        if (configuredRoot.equals(fileUploadRoot) || fileUploadRoot.startsWith(configuredRoot)) {
            throw new IllegalArgumentException("归档临时目录不能指向正式文件目录或其上级目录");
        }
        this.uploadRoot = configuredRoot;
        this.executor = Executors.newFixedThreadPool(Math.max(1, properties.getWorkerThreads()));
        this.parsePermits = new Semaphore(Math.max(1, properties.getMaxConcurrentParses()));
        this.usableSpaceSupplier = usableSpaceSupplier != null
            ? usableSpaceSupplier : () -> uploadRoot.toFile().getUsableSpace();
    }

    public ArchiveUploadService(ArchiveUploadProperties properties, String uploadPath) {
        this(properties, uploadPath, null);
    }

    @PostConstruct
    void initialize() { cleanupOrphanSessionsOnStartup(); }

    public ArchiveUploadProgress createSession(long subjectId, String fileName, long fileSize, int chunkSize,
                                                int totalChunks, String templateId) {
        if (subjectId <= 0 || fileName == null || !fileName.toLowerCase().endsWith(".zip")) throw new IllegalArgumentException("仅支持 ZIP 文件");
        if (fileSize < 0 || fileSize > properties.getMaxSizeBytes()) throw new IllegalArgumentException("ZIP 文件超过大小限制");
        if (chunkSize <= 0 || chunkSize > 128 * 1024 * 1024 || totalChunks <= 0 || totalChunks > 100_000) throw new IllegalArgumentException("分片参数无效");
        if (Math.ceil((double) fileSize / chunkSize) != totalChunks) throw new IllegalArgumentException("分片总数与文件大小不匹配");
        try {
            Files.createDirectories(uploadRoot);
            long required = safeAdd(safeAdd(fileSize, fileSize), safeAdd(properties.getMaxExpandedSizeBytes(), properties.getSafetyReserveBytes()));
            if (usableSpaceSupplier.getAsLong() < required) throw new IllegalStateException("服务器临时空间不足，请清理历史上传或稍后重试");
            String id = UUID.randomUUID().toString();
            Path dir = uploadRoot.resolve(id).normalize();
            Files.createDirectories(dir.resolve("chunks"));
            ArchiveUploadSession s = new ArchiveUploadSession(id, subjectId, fileName, fileSize, chunkSize, totalChunks, templateId, dir);
            sessions.put(id, s);
            return s.snapshot();
        } catch (IOException e) { throw new IllegalStateException("无法创建上传会话", e); }
    }

    public void uploadChunk(long subjectId, String id, int index, InputStream input, long contentLength) {
        ArchiveUploadSession s = session(subjectId, id);
        if (s.status != ArchiveUploadStatus.UPLOADING) throw new IllegalStateException("上传会话已结束");
        if (index < 0 || index >= s.totalChunks) throw new IllegalArgumentException("分片序号无效");
        long expected = index == s.totalChunks - 1 ? s.fileSize - (long) index * s.chunkSize : s.chunkSize;
        if (contentLength >= 0 && contentLength != expected) throw new IllegalArgumentException("分片大小无效");
        try (InputStream in = input) {
            Path part = s.directory.resolve("chunks").resolve(String.format("%08d.part", index));
            long written = 0;
            try (var out = Files.newOutputStream(part, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    written += read;
                    if (written > expected) throw new IllegalArgumentException("分片大小无效");
                    out.write(buffer, 0, read);
                }
            }
            if (written != expected) throw new IllegalArgumentException("分片大小无效");
            synchronized (s) { if (!s.chunks[index]) { s.chunks[index] = true; s.uploadedChunks++; } }
        } catch (IOException e) { throw new IllegalStateException("分片写入失败", e); }
    }

    public ArchiveUploadProgress complete(long subjectId, String id) {
        ArchiveUploadSession s = session(subjectId, id);
        synchronized (s) {
            if (s.status != ArchiveUploadStatus.UPLOADING) return s.snapshot();
            for (boolean present : s.chunks) if (!present) throw new IllegalStateException("仍有分片未上传");
            s.status = ArchiveUploadStatus.MERGING;
        }
        try {
            Path archive = s.directory.resolve("archive.zip");
            try (var out = Files.newOutputStream(archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = 0; i < s.totalChunks; i++) {
                    if (s.cancelRequested.get()) { s.status = ArchiveUploadStatus.CANCELED; cleanupSessionDirectory(s); return s.snapshot(); }
                    Files.copy(s.directory.resolve("chunks").resolve(String.format("%08d.part", i)), out);
                }
            }
            if (!parsePermits.tryAcquire()) {
                s.status = ArchiveUploadStatus.FAILED;
                s.error = "解析任务繁忙，请稍后重试";
                cleanupSessionDirectory(s);
                throw new IllegalStateException(s.error);
            }
            s.status = ArchiveUploadStatus.PARSING;
            try {
                executor.submit(() -> parse(s, archive));
            } catch (RejectedExecutionException e) {
                parsePermits.release();
                s.status = ArchiveUploadStatus.FAILED;
                s.error = "解析服务正在停止，请稍后重试";
                cleanupSessionDirectory(s);
                throw new IllegalStateException(s.error, e);
            }
            return s.snapshot();
        } catch (IOException e) { s.status = ArchiveUploadStatus.FAILED; s.error = "分片合并失败"; cleanupSessionDirectory(s); throw new IllegalStateException(s.error, e); }
    }

    public ArchiveUploadProgress status(long subjectId, String id) { return session(subjectId, id).snapshot(); }

    public void cancel(long subjectId, String id) {
        ArchiveUploadSession s = session(subjectId, id);
        synchronized (s) {
            s.cancelRequested.set(true);
            if (s.status == ArchiveUploadStatus.UPLOADING) {
                s.status = ArchiveUploadStatus.CANCELED;
                cleanupSessionDirectory(s);
            } else if (s.status == ArchiveUploadStatus.MERGING) {
                s.status = ArchiveUploadStatus.CANCELED;
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void parse(ArchiveUploadSession s, Path archive) {
        try {
            preflightArchiveCompatibility(s, archive);
            try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(archive))) {
            int count = 0; long expanded = 0; byte[] buffer = new byte[8192]; ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                if (s.cancelRequested.get()) { s.status = ArchiveUploadStatus.CANCELED; return; }
                if (++count > properties.getMaxEntryCount()) throw new IllegalArgumentException("ZIP 文件条目过多");
                String name = entry.getName(); Path safe = Path.of(name).normalize();
                if (safe.isAbsolute() || safe.startsWith("..")) throw new IllegalArgumentException("ZIP 包含非法路径");
                if (entry.isDirectory()) continue;
                s.currentFile = name; Path target = s.directory.resolve("extracted").resolve(safe).normalize();
                if (!target.startsWith(s.directory.resolve("extracted").normalize())) throw new IllegalArgumentException("ZIP 包含非法路径");
                Files.createDirectories(target.getParent());
                try (var out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    int n; while ((n = zin.read(buffer)) >= 0) { expanded += n; if (expanded > properties.getMaxExpandedSizeBytes()) throw new IllegalArgumentException("ZIP 解压内容超过限制"); out.write(buffer, 0, n); }
                }
                s.processedFiles++;
            }
            s.totalFiles = count; s.currentFile = null;
            matchAndRegister(s);
            s.status = ArchiveUploadStatus.COMPLETED;
            }
        } catch (Exception e) { s.status = s.cancelRequested.get() ? ArchiveUploadStatus.CANCELED : ArchiveUploadStatus.FAILED; s.error = e.getMessage(); }
        finally {
            parsePermits.release();
            cleanupSessionDirectory(s);
        }
    }

    @Scheduled(fixedDelayString = "${multisource.archive-upload.cleanup-interval-ms:3600000}")
    public void cleanupStaleSessions() {
        cleanupDirectories(false);
    }

    /**
     * The session index is intentionally in-memory. Any directory left behind
     * after a restart therefore belongs to an interrupted upload and can be
     * removed immediately during startup.
     */
    private void cleanupOrphanSessionsOnStartup() {
        cleanupDirectories(true);
    }

    private void cleanupDirectories(boolean removeAllOrphans) {
        if (!Files.isDirectory(uploadRoot)) return;
        long cutoff = System.currentTimeMillis() - Math.max(1, properties.getStaleSessionHours()) * 60L * 60L * 1000L;
        try (var dirs = Files.list(uploadRoot)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                String id = dir.getFileName().toString();
                ArchiveUploadSession active = sessions.get(id);
                if (active != null && (active.status == ArchiveUploadStatus.UPLOADING || active.status == ArchiveUploadStatus.MERGING || active.status == ArchiveUploadStatus.PARSING)) return;
                try {
                    if (removeAllOrphans || Files.getLastModifiedTime(dir).toMillis() < cutoff) deleteRecursively(dir);
                } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private void cleanupSessionDirectory(ArchiveUploadSession s) {
        Path dir = s.directory.toAbsolutePath().normalize();
        if (!dir.startsWith(uploadRoot) || dir.equals(uploadRoot)) return;
        try { deleteRecursively(dir); } catch (IOException ignored) { }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        }
    }

    private static long safeAdd(long a, long b) {
        if (a < 0 || b < 0 || Long.MAX_VALUE - a < b) return Long.MAX_VALUE;
        return a + b;
    }

    private void matchAndRegister(ArchiveUploadSession s) {
        var files = new java.util.ArrayList<Path>();
        try (var stream = Files.walk(s.directory.resolve("extracted"))) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        } catch (IOException e) { throw new IllegalStateException("解析文件读取失败", e); }
        files.sort(java.util.Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        var candidates = new java.util.ArrayList<MatchedCandidate>();
        var candidateSlots = new java.util.HashSet<String>();
        for (Path path : files) {
            String name = path.getFileName().toString();
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            String slot = slotFor(s.templateId, name, lower, s);
            if (slot == null || s.matchedSlots.contains(slot)) continue;
            if (!candidateSlots.add(slot)) {
                throw new IllegalArgumentException("ZIP 中存在重复匹配文件：" + slot);
            }
            candidates.add(new MatchedCandidate(slot, path, name));
        }
        var required = requiredSlots(s.templateId);
        for (String slot : required) {
            if (candidates.stream().noneMatch(candidate -> candidate.slot().equals(slot))) {
                s.missingSlots.add(slot);
            }
        }
        if (!s.missingSlots.isEmpty()) {
            throw new IllegalArgumentException("ZIP 缺少必需文件：" + String.join("、", s.missingSlots));
        }
        for (MatchedCandidate candidate : candidates) {
            String id = register(candidate.path(), candidate.name());
            s.matchedFiles.add(new ArchiveUploadProgress.MatchedFile(candidate.slot(), candidate.name(), id));
            s.matchedSlots.add(candidate.slot());
        }
    }

    private void preflightArchiveCompatibility(ArchiveUploadSession s, Path archive) throws IOException {
        var names = new java.util.ArrayList<String>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) names.add(entry.getName());
            }
        }
        String productConflict = productConflict(s.templateId, names);
        if (productConflict != null) throw new IllegalArgumentException(productConflict);
    }

    private String productConflict(String template, java.util.List<String> names) {
        if (template == null) return null;
        String all = String.join("\n", names).toLowerCase(java.util.Locale.ROOT);
        if (template.contains("hy1c-czi") && (all.contains("gf6") || all.contains("gf-6") || all.contains("wfv"))) {
            return "当前选择的是 HY-1C，但上传压缩包疑似为 GF-6 产品，请切换数据类型或重新选择产品包";
        }
        if (template.contains("gf6-wfv") && (all.contains("hy1c") || all.contains("h1c") || all.contains("czi"))) {
            return "当前选择的是 GF-6 WFV，但上传压缩包疑似为 HY-1C 产品，请切换数据类型或重新选择产品包";
        }
        if (template.contains("fy4b-agri") && (all.contains("gf6") || all.contains("hy1c") || all.contains("h1c"))) {
            return "当前选择的是 FY-4B AGRI，但上传压缩包疑似不是 FY-4B 产品，请重新选择产品包";
        }
        if (template.contains("fy3h-mersi") && (all.contains("gf6") || all.contains("hy1c") || all.contains("h1c"))) {
            return "当前选择的是 FY-3H MERSI，但上传压缩包疑似不是 FY-3H 产品，请重新选择产品包";
        }
        return null;
    }

    private java.util.List<String> requiredSlots(String template) {
        if (template == null) return java.util.List.of();
        if (template.contains("gf6-wfv-l1a")) {
            return java.util.List.of("mainTiffTile0", "mainTiffTile1", "mainTiffTile2",
                "metadataXml", "rpcRpb", "nav", "tileIndexTil");
        }
        if (template.contains("hy1c-czi")) return java.util.List.of("main", "metadata", "browse");
        if (template.contains("fy4b-agri")) {
            return java.util.List.of("l1_0500m", "l1_1000m", "l1_2000m", "l1_4000m", "geo_4000m");
        }
        if (template.contains("fy3h-mersi")) return java.util.List.of("observation", "geo1k");
        if (template.contains("hy2d-altimeter")) return java.util.List.of("main");
        return java.util.List.of();
    }

    private record MatchedCandidate(String slot, Path path, String name) {}

    private String slotFor(String template, String name, String lower, ArchiveUploadSession s) {
        if (template == null) return null;
        if (template.contains("gf6-wfv-l1a")) {
            if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
                var m = java.util.regex.Pattern.compile("(?:tile|t|part|block)[-_ ]*0?([1-3])(?:\\D|$)").matcher(lower);
                if (m.find()) return "mainTiffTile" + (Integer.parseInt(m.group(1)) - 1);
                long count = s.matchedSlots.stream().filter(x -> x.startsWith("mainTiffTile")).count();
                return count < 3 ? "mainTiffTile" + count : null;
            }
            if (lower.endsWith(".xml")) return "metadataXml";
            if (lower.endsWith(".rpb")) return "rpcRpb";
            if (lower.endsWith(".nav")) return "nav";
            if (lower.endsWith(".til")) return "tileIndexTil";
            return null;
        }
        if (template.contains("fy4b-agri")) {
            if (!lower.endsWith(".hdf")) return null;
            if (lower.contains("0500m") || lower.contains("500m")) return "l1_0500m";
            if (lower.contains("1000m") || lower.contains("1km")) return "l1_1000m";
            if (lower.contains("2000m") || lower.contains("2km")) return "l1_2000m";
            if (lower.contains("4000m") || lower.contains("4km")) return lower.contains("geo") ? "geo_4000m" : "l1_4000m";
        }
        if (template.contains("hy1c-czi")) {
            if (lower.endsWith(".h5") || lower.endsWith(".hdf5")) return "main";
            if (lower.endsWith(".xml")) return "metadata";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "browse";
        }
        if (template.contains("fy3h-mersi")) {
            if (lower.endsWith(".hdf")) return lower.contains("geo") ? "geo1k" : "observation";
        }
        if (template.contains("hy2d-altimeter") && lower.endsWith(".tar.gz")) return "main";
        return null;
    }

    private String register(Path path, String name) {
        if (sysFileService == null) return Long.toUnsignedString(name.hashCode());
        SysFile file = sysFileService.upload(new PathMultipartFile(path, name));
        if (file == null || file.getId() == null) throw new IllegalStateException("文件注册失败");
        return String.valueOf(file.getId());
    }

    private static final class PathMultipartFile implements MultipartFile {
        private final Path path; private final String name;
        PathMultipartFile(Path path, String name) { this.path = path; this.name = name; }
        public String getName() { return name; }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return "application/octet-stream"; }
        public boolean isEmpty() { try { return Files.size(path) == 0; } catch (IOException e) { return true; } }
        public long getSize() { try { return Files.size(path); } catch (IOException e) { return 0; } }
        public byte[] getBytes() throws IOException { return Files.readAllBytes(path); }
        public java.io.InputStream getInputStream() throws IOException { return Files.newInputStream(path); }
        public void transferTo(java.io.File dest) throws IOException { Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING); }
    }

    private ArchiveUploadSession session(long subjectId, String id) {
        ArchiveUploadSession s = sessions.get(id);
        if (s == null || s.subjectId != subjectId) throw new IllegalArgumentException("上传会话不存在");
        return s;
    }
}
