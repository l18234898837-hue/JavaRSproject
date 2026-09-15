package com.htht.config.multisource.archive;

import com.htht.system.domain.SysFile;
import com.htht.system.service.ISysFileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("dev")
class ArchiveUploadServiceTest {
    @TempDir
    Path temp;

    @Test
    void rejectsIncompleteChunksBeforeMerge() {
        ArchiveUploadService service = service();
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", 4, 2, 2, "gf6-wfv-l1a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(new byte[]{1, 2}), 2);

        assertThatThrownBy(() -> service.complete(7, created.uploadId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("分片未上传");
    }

    @Test
    void rejectsChunkBodyLargerThanDeclaredSize() {
        ArchiveUploadService service = service();
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", 4, 4, 1, "gf6-wfv-l1a-v1");

        assertThatThrownBy(() -> service.uploadChunk(7, created.uploadId(), 0,
            new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}), 4))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("分片大小");
    }

    @Test
    void rejectsZipPathTraversal() throws Exception {
        byte[] zip = zip("../escape.tif", new byte[]{1});
        ArchiveUploadService service = service();
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", zip.length, zip.length, 1, "gf6-wfv-l1a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).isEqualTo(ArchiveUploadStatus.FAILED);
        assertThat(progress.error()).contains("非法路径");
    }

    @Test
    void acceptsInvalidUtf8BytesInCentralDirectoryEntryName() throws Exception {
        byte[] zip = zip("12345678", new byte[]{42});
        byte[] malformedNameZip = replaceCentralDirectoryName(zip,
            new byte[]{(byte) 0xff, (byte) 0xfe, 't', 'r', 'y', '.', 't', 'x'});
        ArchiveUploadService service = service();
        ArchiveUploadProgress created = service.createSession(7, "legacy.zip", malformedNameZip.length,
            malformedNameZip.length, 1, null);
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(malformedNameZip), malformedNameZip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).as(progress.error()).isEqualTo(ArchiveUploadStatus.COMPLETED);
    }

    @Test
    void parsesGf6FilesAndRegistersSysFiles() throws Exception {
        byte[] zip = zipEntries(
            new String[]{"scene_tile1.tif", "scene_tile2.tif", "scene_tile3.tif", "scene.xml", "scene.rpb", "scene.nav", "scene.til"},
            new byte[]{1, 2, 3, 4, 5, 6, 7});
        ISysFileService files = mock(ISysFileService.class);
        when(files.upload(any())).thenAnswer(invocation -> {
            org.springframework.web.multipart.MultipartFile file = invocation.getArgument(0);
            SysFile result = new SysFile();
            result.setId((long) file.getOriginalFilename().hashCode() * -1L + 100);
            result.setFileName(file.getOriginalFilename());
            result.setExtension(file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.') + 1));
            result.setFilePath(temp.resolve(file.getOriginalFilename()).toString());
            Files.copy(file.getInputStream(), Path.of(result.getFilePath()));
            return result;
        });
        ArchiveUploadService service = service(files);
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", zip.length, zip.length, 1, "gf6-wfv-l1a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).isEqualTo(ArchiveUploadStatus.COMPLETED);
        assertThat(progress.matchedSlots()).containsExactlyInAnyOrder("mainTiffTile0", "mainTiffTile1", "mainTiffTile2", "metadataXml", "rpcRpb", "nav", "tileIndexTil");
        assertThat(progress.matchedFiles()).allSatisfy(file -> assertThat(file.sysFileId()).isNotBlank());
        assertThat(Files.exists(temp.resolve("sessions").resolve(created.uploadId()))).isFalse();
    }

    @Test
    void parsesRealGf6SuffixTilesAndIgnoresRpbAuxXml() throws Exception {
        byte[] zip = zipEntries(
            new String[]{
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603-1.tiff",
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603-2.tiff",
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603-3.tiff",
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.xml",
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603-1.rpb.aux.xml",
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.rpb",
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.nav",
                "GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.til"},
            new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
        ISysFileService files = mock(ISysFileService.class);
        when(files.upload(any())).thenAnswer(invocation -> {
            org.springframework.web.multipart.MultipartFile file = invocation.getArgument(0);
            SysFile result = new SysFile();
            result.setId((long) file.getOriginalFilename().hashCode() * -1L + 100);
            result.setFileName(file.getOriginalFilename());
            return result;
        });
        ArchiveUploadService service = service(files);
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", zip.length, zip.length, 1, "gf6-wfv-l1a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).isEqualTo(ArchiveUploadStatus.COMPLETED);
        assertThat(progress.matchedSlots()).containsExactlyInAnyOrder(
            "mainTiffTile0", "mainTiffTile1", "mainTiffTile2", "metadataXml", "rpcRpb", "nav", "tileIndexTil");
        assertThat(progress.matchedFiles().stream().filter(file -> file.slotId().equals("metadataXml")))
            .extracting(ArchiveUploadProgress.MatchedFile::fileName)
            .containsExactly("GF6_WFV_E107.6_N9.0_20260716_L1A1420724603.xml");
    }

    @Test
    void ignoresPerTileRpbSidecarsWhenParsingGf6Archive() throws Exception {
        byte[] zip = zipEntries(
            new String[]{"scene_tile1.tif", "scene_tile2.tif", "scene_tile3.tif",
                "scene_tile1.rpb", "scene_tile2.rpb", "scene_tile3.rpb",
                "scene.rpb", "scene.xml", "scene.nav", "scene.til"},
            new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        ISysFileService files = mock(ISysFileService.class);
        when(files.upload(any())).thenAnswer(invocation -> {
            org.springframework.web.multipart.MultipartFile file = invocation.getArgument(0);
            SysFile result = new SysFile();
            result.setId((long) file.getOriginalFilename().hashCode() * -1L + 100);
            result.setFileName(file.getOriginalFilename());
            return result;
        });
        ArchiveUploadService service = service(files);
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", zip.length, zip.length, 1, "gf6-wfv-l1a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).isEqualTo(ArchiveUploadStatus.COMPLETED);
        assertThat(progress.matchedSlots()).contains("rpcRpb");
        assertThat(progress.matchedFiles().stream().filter(file -> file.slotId().equals("rpcRpb")))
            .extracting(ArchiveUploadProgress.MatchedFile::fileName)
            .containsExactly("scene.rpb");
    }

    @Test
    void rejectsHy1cArchiveWithoutMainFileBeforeRegisteringPartialMatches() throws Exception {
        byte[] zip = zipEntries(
            new String[]{"GF6_scene.xml", "GF6_scene.jpg"},
            new byte[]{1, 2});
        ISysFileService files = mock(ISysFileService.class);
        ArchiveUploadService service = service(files);
        ArchiveUploadProgress created = service.createSession(
            7, "wrong.zip", zip.length, zip.length, 1, "hy1c-czi-l2a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).isEqualTo(ArchiveUploadStatus.FAILED);
        assertThat(progress.error()).contains("HY-1C").contains("GF-6");
        assertThat(progress.matchedFiles()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(files);
    }

    @Test
    void rejectsFy3ArchiveWhenGf6IsSelectedBeforeRegisteringFiles() throws Exception {
        byte[] zip = zipEntries(
            new String[]{"FY3H_MERSI_1000M.HDF", "FY3H_MERSI_GEO1K.HDF"},
            new byte[]{1, 2});
        ISysFileService files = mock(ISysFileService.class);
        ArchiveUploadService service = service(files);
        ArchiveUploadProgress created = service.createSession(
            7, "fy3h.zip", zip.length, zip.length, 1, "gf6-wfv-l1a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).isEqualTo(ArchiveUploadStatus.FAILED);
        assertThat(progress.error()).contains("GF-6 WFV").contains("FY-3H MERSI");
        assertThat(progress.matchedFiles()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(files);
    }

    @Test
    void cleansSessionDirectoryAfterFailedParse() throws Exception {
        byte[] zip = zip("../escape.tif", new byte[]{1});
        ArchiveUploadService service = service();
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", zip.length, zip.length, 1, "gf6-wfv-l1a-v1");
        service.uploadChunk(7, created.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, created.uploadId());

        ArchiveUploadProgress progress = awaitFinished(service, created.uploadId());
        assertThat(progress.status()).isEqualTo(ArchiveUploadStatus.FAILED);
        assertThat(Files.exists(temp.resolve("sessions").resolve(created.uploadId()))).isFalse();
    }

    @Test
    void cancelCleansUploadDirectory() throws Exception {
        ArchiveUploadService service = service();
        ArchiveUploadProgress created = service.createSession(7, "sample.zip", 4, 4, 1, "gf6-wfv-l1a-v1");
        service.cancel(7, created.uploadId());

        assertThat(service.status(7, created.uploadId()).status()).isEqualTo(ArchiveUploadStatus.CANCELED);
        assertThat(Files.exists(temp.resolve("sessions").resolve(created.uploadId()))).isFalse();
    }

    @Test
    void rejectsSessionWhenTemporaryDiskSpaceIsInsufficient() {
        ArchiveUploadProperties properties = properties();
        properties.setSafetyReserveBytes(100);
        properties.setMaxExpandedSizeBytes(100);
        ArchiveUploadService service = new ArchiveUploadService(properties, temp.resolve("uploads").toString(), mock(ISysFileService.class), () -> 150L);

        assertThatThrownBy(() -> service.createSession(7, "sample.zip", 100, 100, 1, "gf6-wfv-l1a"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("临时空间不足");
    }

    @Test
    void limitsConcurrentParsing() throws Exception {
        byte[] zip = zipEntries(
            new String[]{"scene.h5", "scene.xml", "scene.jpg"},
            new byte[]{1, 2, 3});
        ArchiveUploadProperties properties = properties();
        properties.setMaxConcurrentParses(1);
        AtomicInteger uploads = new AtomicInteger();
        CountDownLatch parseStarted = new CountDownLatch(1);
        CountDownLatch releaseParse = new CountDownLatch(1);
        ISysFileService files = mock(ISysFileService.class);
        when(files.upload(any())).thenAnswer(invocation -> {
            uploads.incrementAndGet();
            parseStarted.countDown();
            releaseParse.await();
            SysFile result = new SysFile();
            result.setId(1L);
            result.setFileName("scene.xml");
            return result;
        });
        ArchiveUploadService service = new ArchiveUploadService(properties, temp.resolve("uploads").toString(), files, () -> Long.MAX_VALUE);
        ArchiveUploadProgress first = service.createSession(7, "one.zip", zip.length, zip.length, 1, "hy1c-czi-l2a-v1");
        service.uploadChunk(7, first.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        service.complete(7, first.uploadId());
        assertThat(parseStarted.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        ArchiveUploadProgress second = service.createSession(7, "two.zip", zip.length, zip.length, 1, "hy1c-czi-l2a-v1");
        service.uploadChunk(7, second.uploadId(), 0, new ByteArrayInputStream(zip), zip.length);
        assertThatThrownBy(() -> service.complete(7, second.uploadId()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("解析任务繁忙");
        releaseParse.countDown();
        assertThat(awaitFinished(service, first.uploadId()).status()).isEqualTo(ArchiveUploadStatus.COMPLETED);
    }

    @Test
    void staleCleanupDoesNotDeleteActiveSessionOrOutsideDirectory() throws Exception {
        ArchiveUploadService service = service();
        ArchiveUploadProgress active = service.createSession(7, "sample.zip", 4, 4, 1, "gf6-wfv-l1a");
        Path old = temp.resolve("sessions").resolve("orphan");
        Files.createDirectories(old);
        Files.writeString(old.resolve("archive.zip"), "orphan");
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 3_600_000));
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("keep.txt"), "keep");

        service.cleanupStaleSessions();

        assertThat(Files.exists(temp.resolve("sessions").resolve(active.uploadId()))).isTrue();
        assertThat(Files.exists(old)).isFalse();
        assertThat(Files.exists(outside.resolve("keep.txt"))).isTrue();
    }

    @Test
    void startupCleanupRemovesRecentOrphanSession() throws Exception {
        ArchiveUploadService service = service();
        Path orphan = temp.resolve("sessions").resolve("orphan-recent");
        Files.createDirectories(orphan);
        Files.writeString(orphan.resolve("chunks.part"), "orphan");

        service.initialize();

        assertThat(Files.exists(orphan)).isFalse();
    }

    @Test
    void rejectsArchiveRootThatCouldContainFormalUploads() {
        ArchiveUploadProperties properties = new ArchiveUploadProperties();
        properties.setRootDirectory(temp.resolve("uploads").toString());

        assertThatThrownBy(() -> new ArchiveUploadService(properties, temp.resolve("uploads").toString(), mock(ISysFileService.class)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("正式文件目录");
    }

    private ArchiveUploadService service() { return service(mock(ISysFileService.class)); }

    private ArchiveUploadService service(ISysFileService files) {
        return new ArchiveUploadService(properties(), temp.resolve("uploads").toString(), files);
    }

    private ArchiveUploadProperties properties() {
        ArchiveUploadProperties properties = new ArchiveUploadProperties();
        properties.setRootDirectory(temp.resolve("sessions").toString());
        properties.setWorkerThreads(1);
        properties.setSafetyReserveBytes(0);
        properties.setMaxExpandedSizeBytes(10_000);
        properties.setStaleSessionHours(1);
        return properties;
    }

    private static ArchiveUploadProgress awaitFinished(ArchiveUploadService service, String id) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            ArchiveUploadProgress progress = service.status(7, id);
            if (progress.status() == ArchiveUploadStatus.COMPLETED || progress.status() == ArchiveUploadStatus.FAILED || progress.status() == ArchiveUploadStatus.CANCELED) return progress;
            Thread.sleep(10);
        }
        throw new AssertionError("archive parse did not finish");
    }

    private static byte[] zip(String name, byte[] data) throws IOException { return zipEntries(new String[]{name}, data); }

    private static byte[] zipEntries(String[] names, byte[] data) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (int i = 0; i < names.length; i++) {
                out.putNextEntry(new ZipEntry(names[i]));
                out.write(data[i % data.length]);
                out.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] replaceCentralDirectoryName(byte[] zip, byte[] replacement) {
        byte[] result = Arrays.copyOf(zip, zip.length);
        int central = -1;
        for (int i = 0; i + 3 < result.length; i++) {
            if ((result[i] & 0xff) == 0x50 && (result[i + 1] & 0xff) == 0x4b
                && (result[i + 2] & 0xff) == 0x01 && (result[i + 3] & 0xff) == 0x02) {
                central = i;
                break;
            }
        }
        if (central < 0) throw new AssertionError("central directory not found");
        // Mark the legacy entry as non-UTF-8, matching archives that trigger
        // JDK 17's strict invalid-CEN-name check.
        result[central + 9] = (byte) (result[central + 9] & 0xF7);
        for (int i = 0; i + 3 < central; i++) {
            if ((result[i] & 0xff) == 0x50 && (result[i + 1] & 0xff) == 0x4b
                && (result[i + 2] & 0xff) == 0x03 && (result[i + 3] & 0xff) == 0x04) {
                result[i + 7] = (byte) (result[i + 7] & 0xF7);
                break;
            }
        }
        int nameLength = (result[central + 28] & 0xff) | ((result[central + 29] & 0xff) << 8);
        if (nameLength != replacement.length) throw new AssertionError("unexpected entry name length");
        System.arraycopy(replacement, 0, result, central + 46, replacement.length);
        return result;
    }
}
