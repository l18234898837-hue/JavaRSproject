# 多源产品包服务端分片解析设计

## 目标

为多源卫星数据处理前端提供可用于大 ZIP（最大 20 GB）的服务端分片上传、合并、异步解析、进度查询和取消能力，消除当前 `/api/multisource/archive-upload/*` 404，并与现有多源任务接口及文件存储约定兼容。

## 已确认现状

- 前端已调用以下接口：
  - `POST /api/multisource/archive-upload/sessions`
  - `PUT /api/multisource/archive-upload/sessions/{uploadId}/chunks/{chunkIndex}`
  - `POST /api/multisource/archive-upload/sessions/{uploadId}/complete`
  - `GET /api/multisource/archive-upload/sessions/{uploadId}/status`
  - `POST /api/multisource/archive-upload/sessions/{uploadId}/cancel`
- 当前 Java 后端 `htht-admin` 与 `htht-multisource` 中不存在 `archive-upload` Controller，8080 服务访问该路径返回 404。
- 后端已有多源 Controller、统一 `R<T>` 返回结构、Sa-Token 鉴权和 `file.upload-path` 文件根目录。
- 前端暂时强制服务端解析，不再回退到浏览器本地 ZIP 解析。

## 设计方案

### 1. 接口层

新增 `ArchiveUploadController`，路径为 `/api/multisource/archive-upload`，所有接口沿用当前登录用户鉴权和 `R<T>` 响应包装。

创建会话请求字段：`fileName`、`fileSize`、`chunkSize`、`totalChunks`、`templateId`。服务端重新校验大小、分片大小、分片数量和 ZIP 文件名，最大文件大小默认 20 GiB，可通过 `multisource.archive-upload.max-size-bytes` 配置覆盖但不得超过 20 GiB。

状态响应字段固定为：`uploadId`、`status`（`UPLOADING`/`MERGING`/`PARSING`/`COMPLETED`/`FAILED`/`CANCELED`）、`uploadedChunks`、`totalChunks`、`processedFiles`、可选 `totalFiles`、`currentFile`、`matchedFiles`、`matchedSlots`、`missingSlots`、`error`。

### 2. 会话与文件存储

新增无数据库依赖的会话服务，使用内存会话索引配合磁盘目录保存大文件，避免把 ZIP 或分片载入 JVM 堆：

```text
{file.upload-path}/.multisource-archive/{uploadId}/
  manifest.json
  chunks/00000000.part
  chunks/00000001.part
  archive.zip
  extracted/
```

会话目录名只使用服务端生成的 UUID；原始文件名只作为元数据保存，禁止参与路径拼接。服务重启后未完成会话视为失效并返回会话不存在，已完成的产品文件仍保留在现有文件根目录。

### 3. 分片上传与合并

- 分片接口使用 `application/octet-stream` 请求体和路径中的 `chunkIndex`。
- 服务端校验索引范围、请求体长度和预期分片大小；重复上传同一分片允许幂等覆盖，但不能越界。
- 完成接口检查所有分片齐全后按序流式合并到 `archive.zip`，状态切换为 `MERGING`，合并成功后异步进入 `PARSING`。
- 任意阶段失败都记录安全可展示的错误信息并切换 `FAILED`；临时目录可在失败/取消后清理。

### 4. 异步解析

使用受控线程池执行 ZIP 解析，避免阻塞 HTTP 请求线程。解析过程采用 `ZipInputStream` 流式读取，先统计安全条目数量，再按当前多源产品模板规则匹配文件名。禁止绝对路径和 `..` 路径穿越；限制单条目解压大小、总解压大小和条目数量，防止 ZIP 炸弹。

每处理一个文件更新会话进度（文件总数、已处理数、当前文件）。匹配到的文件写入现有文件上传根目录并生成前端所需的 `matchedFiles` 信息；解析结束后计算 `missingSlots`，状态切换为 `COMPLETED`。

### 5. 取消与并发

取消接口将会话标记为 `CANCELED`，中断解析任务并删除未完成临时文件；解析线程在每个 ZIP 条目和分片边界检查取消标志。会话只允许创建者访问，状态变更使用同步/并发安全结构，避免重复 complete、cancel 与状态覆盖。

### 6. 配置

新增配置项：

```yaml
multisource:
  archive-upload:
    max-size-bytes: 21474836480
    max-entry-count: 100000
    max-expanded-size-bytes: 42949672960
    worker-threads: 2
```

默认临时根目录复用 `${file.upload-path}/.multisource-archive`，不新增外部基础设施。

## 测试与验收

1. 单元测试覆盖：会话创建校验、分片索引/大小校验、缺片拒绝合并、状态转换、取消、路径穿越和 ZIP 条目限制。
2. Controller 测试覆盖五个端点的请求/响应字段和 `R<T>` 包装。
3. Maven 聚焦测试：`mvn -pl htht-admin,htht-modules/htht-multisource -am test`。
4. 启动后使用本机 8080 探测创建会话接口，确认不再返回 404；再用小型 ZIP 完成分片、合并、状态轮询和取消验证。
5. 不修改现有多源处理任务接口，不改变前端 API 路径。

## 风险与边界

- 内存会话索引适合单实例部署；若后端部署多实例，需要后续将会话元数据迁移到 Redis 或数据库，本次不引入该范围外依赖。
- 20 GB 是产品级上限，实际可用上限还受磁盘空间、反向代理请求体限制和文件系统配额影响；部署时需同步调整 Nginx/网关超时与请求体配置。
- 后端重启会使未完成上传会话失效，因此前端需允许用户重新选择 ZIP。
