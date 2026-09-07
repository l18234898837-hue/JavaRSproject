package com.htht.web.controller.multisource;

import com.htht.common.core.domain.R;
import com.htht.config.multisource.archive.ArchiveUploadProgress;
import com.htht.config.multisource.archive.ArchiveUploadService;
import com.htht.multisource.domain.model.AccessSubject;
import com.htht.web.controller.multisource.dto.CreateArchiveUploadRequest;
import com.htht.web.controller.multisource.dto.CreateArchiveUploadResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/multisource/archive-upload")
public class ArchiveUploadController {
    private final ArchiveUploadService service;
    private final Supplier<AccessSubject> currentSubject;
    public ArchiveUploadController(ArchiveUploadService service, Supplier<AccessSubject> currentSubject) { this.service = service; this.currentSubject = currentSubject; }
    @PostMapping("/sessions")
    public ResponseEntity<R<CreateArchiveUploadResponse>> create(@RequestBody CreateArchiveUploadRequest request) {
        ArchiveUploadProgress p = service.createSession(currentSubject.get().userId(), request.fileName(), request.fileSize(), request.chunkSize(), request.totalChunks(), request.templateId());
        return ResponseEntity.ok(R.ok(new CreateArchiveUploadResponse(p.uploadId())));
    }
    @PutMapping(value = "/sessions/{uploadId}/chunks/{chunkIndex}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<R<Void>> chunk(@PathVariable String uploadId, @PathVariable int chunkIndex, javax.servlet.http.HttpServletRequest request) throws IOException {
        service.uploadChunk(currentSubject.get().userId(), uploadId, chunkIndex, request.getInputStream(), request.getContentLengthLong()); return ResponseEntity.ok(R.ok());
    }
    @PostMapping("/sessions/{uploadId}/complete")
    public ResponseEntity<R<Void>> complete(@PathVariable String uploadId) { service.complete(currentSubject.get().userId(), uploadId); return ResponseEntity.ok(R.ok()); }
    @GetMapping("/sessions/{uploadId}/status")
    public ResponseEntity<R<ArchiveUploadProgress>> status(@PathVariable String uploadId) { return ResponseEntity.ok(R.ok(service.status(currentSubject.get().userId(), uploadId))); }
    @PostMapping("/sessions/{uploadId}/cancel")
    public ResponseEntity<R<Void>> cancel(@PathVariable String uploadId) { service.cancel(currentSubject.get().userId(), uploadId); return ResponseEntity.ok(R.ok()); }
}
