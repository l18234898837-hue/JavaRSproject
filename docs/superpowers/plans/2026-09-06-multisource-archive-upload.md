# 多源产品包服务端分片解析 Implementation Plan

> For agentic workers: REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 在 Java 后端实现与前端契约一致的 20 GB ZIP 分片上传、合并、异步解析、状态查询和取消能力。

**Architecture:** 在 htht-admin 增加 REST Controller 和应用服务；会话元数据保存在单实例内存索引，分片及归档落盘到 file.upload-path/.multisource-archive/{uploadId}。解析任务使用受控线程池异步执行，按 ZIP 流式读取并通过模板规则匹配产品文件。

**Tech Stack:** Spring Boot MVC、Java NIO、ZipInputStream、JUnit/Spring Boot Test、现有 R<T> 返回结构与 Sa-Token 鉴权。

**Spec:** docs/superpowers/specs/2026-09-06-multisource-archive-upload-design.md

## Global Constraints

- API 路径必须保持为 /api/multisource/archive-upload。
- 最大 ZIP 默认 20 GiB（21474836480 bytes）。
- 不把整个 ZIP 或分片读入 JVM 堆。
- 所有状态接口返回现有 R<T> 包装。
- 禁止绝对路径、.. 路径穿越和 ZIP 炸弹风险。
- 本次仅支持单实例内存会话，不引入 Redis/数据库。
- 前端现有多源处理任务接口和请求字段不得改变。

---

### Task 1: 定义归档上传领域模型与配置

**Files:**
- Create: htht-admin/src/main/java/com/htht/config/multisource/archive/ArchiveUploadStatus.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/archive/ArchiveUploadProgress.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/archive/ArchiveUploadProperties.java
- Modify: htht-admin/src/main/resources/application.yml
- Test: htht-admin/src/test/java/com/htht/config/multisource/archive/ArchiveUploadPropertiesTest.java

**Interfaces:**
- ArchiveUploadStatus: enum UPLOADING, MERGING, PARSING, COMPLETED, FAILED, CANCELED.
- ArchiveUploadProgress: immutable response model with uploadId,status,uploadedChunks,totalChunks,processedFiles,totalFiles,currentFile,matchedFiles,matchedSlots,missingSlots,error.
- ArchiveUploadProperties: maxSizeBytes,maxEntryCount,maxExpandedSizeBytes,workerThreads,rootDirectory and defaults from spec.

- [ ] Step 1: Write the failing test asserting defaults are 20 GiB, 100000 entries, 40 GiB expanded size, 2 worker threads.
- [ ] Step 2: Run mvn -pl htht-admin -Dtest=ArchiveUploadPropertiesTest test and verify it fails because the properties class does not exist.
- [ ] Step 3: Implement models and @ConfigurationProperties(prefix="multisource.archive-upload") binding.
- [ ] Step 4: Run focused test and verify it passes.
- [ ] Step 5: Commit changes with message feat: add archive upload domain models.

### Task 2: Implement disk-backed upload session and chunk lifecycle

**Files:**
- Create: htht-admin/src/main/java/com/htht/config/multisource/archive/ArchiveUploadSession.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/archive/ArchiveUploadService.java
- Test: htht-admin/src/test/java/com/htht/config/multisource/archive/ArchiveUploadServiceTest.java

**Interfaces:**
- createSession(subjectId,fileName,fileSize,chunkSize,totalChunks,templateId) returns ArchiveUploadProgress.
- uploadChunk(subjectId,uploadId,chunkIndex,InputStream,contentLength).
- complete(subjectId,uploadId) returns ArchiveUploadProgress.
- getStatus(subjectId,uploadId) returns ArchiveUploadProgress.
- cancel(subjectId,uploadId).

- [ ] Step 1: Write tests for valid session creation, rejecting over-20-GiB input, rejecting invalid indexes, rejecting complete with missing chunks, and idempotent repeated chunk upload.
- [ ] Step 2: Run focused tests and verify expected failures.
- [ ] Step 3: Implement UUID session directories and streaming chunk writes under configured root.
- [ ] Step 4: Implement ordered streaming merge to archive.zip and transition to PARSING.
- [ ] Step 5: Run focused tests and verify passes.
- [ ] Step 6: Commit changes with message feat: add disk-backed archive upload sessions.

### Task 3: Implement secure asynchronous ZIP parser and product matching

**Files:**
- Create: htht-admin/src/main/java/com/htht/config/multisource/archive/ArchiveProductParser.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/archive/ArchiveUploadService.java
- Test: htht-admin/src/test/java/com/htht/config/multisource/archive/ArchiveProductParserTest.java

**Interfaces:**
- parse(ArchiveUploadSession session) streams entries, updates progress, writes matched files, and completes/fails/cancels the session.
- ZIP policy rejects absolute paths, normalized paths escaping extraction root, entry count over maxEntryCount, and expanded bytes over maxExpandedSizeBytes.
- Matching result fields use slotId,fileName,sysFileId; missingSlots is returned when no match exists.

- [ ] Step 1: Write failing tests for valid ZIP matching, path traversal rejection, entry-count limit, expanded-size limit, and cancellation.
- [ ] Step 2: Run parser tests and verify failures.
- [ ] Step 3: Implement streaming parser with ZipInputStream, safe extraction, progress updates and cancellation checks.
- [ ] Step 4: Connect parser completion to service status and matched/missing fields.
- [ ] Step 5: Run parser tests and verify passes.
- [ ] Step 6: Commit changes with message feat: securely parse multisource archives.

### Task 4: Expose REST endpoints and map request/response DTOs

**Files:**
- Create: htht-admin/src/main/java/com/htht/web/controller/multisource/ArchiveUploadController.java
- Create: htht-admin/src/main/java/com/htht/web/controller/multisource/dto/ArchiveUploadDtos.java
- Modify: htht-admin/src/main/java/com/htht/web/controller/multisource/MultisourceExceptionHandler.java
- Test: htht-admin/src/test/java/com/htht/web/controller/multisource/ArchiveUploadControllerTest.java

**Interfaces:**
- POST /sessions returns R<CreateArchiveUploadResponse>.
- PUT /sessions/{uploadId}/chunks/{chunkIndex} receives octet-stream and returns R<Void>.
- POST /sessions/{uploadId}/complete returns R<Void>.
- GET /sessions/{uploadId}/status returns R<ArchiveUploadProgress>.
- POST /sessions/{uploadId}/cancel returns R<Void>.

- [ ] Step 1: Write MockMvc tests for five routes, request validation and R<T> response fields.
- [ ] Step 2: Run controller tests and verify failures.
- [ ] Step 3: Implement DTOs and controller mappings with application/octet-stream streaming body.
- [ ] Step 4: Add exception mapping for invalid session, validation and archive security failures.
- [ ] Step 5: Run controller tests and verify passes.
- [ ] Step 6: Commit changes with message feat: expose archive upload REST API.

### Task 5: Integrate runtime configuration, run full verification and smoke test

**Files:**
- Modify: htht-admin/src/main/resources/application-dev.yml
- Modify: htht-admin/src/main/resources/application-prod.yml
- Create: htht-admin/src/test/java/com/htht/config/multisource/archive/ArchiveUploadIntegrationTest.java

- [ ] Step 1: Add explicit archive-upload configuration to dev and prod profiles, keeping 20 GiB default.
- [ ] Step 2: Add integration test covering create, upload chunk, complete and status.
- [ ] Step 3: Run mvn -pl htht-admin -Dtest='*ArchiveUpload*' test.
- [ ] Step 4: Run mvn -pl htht-admin,htht-modules/htht-multisource -am test.
- [ ] Step 5: Build backend with mvn -pl htht-admin -am package -DskipTests.
- [ ] Step 6: Restart deployed backend, then probe POST http://127.0.0.1:8080/api/multisource/archive-upload/sessions. Expected response is not 404; 401 is acceptable without login.
- [ ] Step 7: Commit changes with message test: verify archive upload runtime integration.
