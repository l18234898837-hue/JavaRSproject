# 历史任务与产物保留设计

## 目标与范围

新增独立的“历史任务与产物”入口和弹窗，展示当前用户创建的处理任务，并允许用户按任务整体管理产物。第一期支持历史查询、任务详情、单文件查看/下载、ZIP 下载、质量报告、长期保留、移入回收站、恢复和显式确认后的永久删除。

第一期不做自动过期清理、容量配额、单个产物删除、管理员跨用户查询，也不使用 `rs_legacy_plan_archive` 表。原始上传文件不属于任务产物清理范围。

## 现状与约束

- `ProcessingExecutionController` 已提供任务进度、质量报告、单产物下载、ZIP 下载、图片预览和栅格瓦片接口。
- `GetTaskDetailService` 已通过 `task.creator == subject` 校验任务归属。
- `LocalWorkerResultArchiveService` 从工作目录、提升后的产物目录和 ZIP 缓存读取文件。
- `WorkerArtifactRasterPreviewService` 持有元数据和 PNG 瓦片进程内缓存。
- 产物元数据保存于 `rs_artifact`，文件路径来自任务/阶段快照和受限 storage key，不能信任前端传入路径。

## 状态模型

`rs_task_artifact_retention` 以 `task_id` 为主键，状态枚举为：

- `AVAILABLE`：正常可查看和下载。
- `PINNED`：用户主动长期保留，可查看和下载。
- `RECYCLE_BIN`：已移入回收站，文件尚未物理删除，不可查看或下载，可恢复。
- `PURGING`：异步物理清理中，不可查看或下载。
- `PURGE_FAILED`：物理清理失败，保留任务和元数据，不可查看或下载，可安全重试。
- `PURGED`：物理清理完成，仅保留任务、阶段、事件、质量结果和产物元数据。
- `MISSING`：初始化扫描或访问校验发现元数据存在但文件不存在，不可查看或下载，并提示文件缺失。

状态转换：

```text
AVAILABLE <-> PINNED
AVAILABLE/PINNED -> RECYCLE_BIN -> AVAILABLE
RECYCLE_BIN -> PURGING -> PURGED
PURGING -> PURGE_FAILED -> PURGING
AVAILABLE/PINNED -> MISSING
```

`PURGE` 只允许由任务创建者发起，且任务必须处于 `SUCCEEDED`、`FAILED` 或 `CANCELED`；`CREATED`、`QUEUED`、`RUNNING`、`RECOVERY_PENDING`、`CANCELING` 一律拒绝。`PURGE_FAILED` 不重置为可用，避免文件集合部分删除时产生假可用状态。

移入回收站是对任务产物的整体覆盖操作，会清除 `pinned`；恢复后统一回到 `AVAILABLE`，用户如需长期保留可以再次执行 `PIN`。因此 `pinned` 始终只表示当前状态是否为 `PINNED`。

## 数据库迁移

新增 Flyway 脚本 `V20260929_01__create_task_artifact_retention.sql`：

```sql
CREATE TABLE rs_task_artifact_retention (
    task_id bigint PRIMARY KEY REFERENCES rs_processing_task(task_id),
    status varchar(32) NOT NULL,
    pinned boolean NOT NULL DEFAULT false,
    recycled_at timestamptz,
    purge_started_at timestamptz,
    purged_at timestamptz,
    operator_id bigint,
    failure_reason text,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (status IN ('AVAILABLE','PINNED','RECYCLE_BIN','PURGING','PURGE_FAILED','PURGED','MISSING')),
    CHECK ((status = 'PINNED') = pinned)
);
CREATE INDEX idx_rs_task_artifact_retention_status_time
    ON rs_task_artifact_retention(status, purge_started_at);
CREATE INDEX idx_rs_task_product_product_code
    ON rs_task_product(product_code);
CREATE INDEX idx_rs_processing_task_creator_updated
    ON rs_processing_task(creator_id, updated_at DESC);
```

迁移只创建表和索引，不删除或改写历史任务。历史任务初始化由一次性只读扫描完成：按任务产物元数据解析受信任路径，全部存在且校验和匹配则插入 `AVAILABLE`；任一注册产物缺失则插入 `MISSING`。扫描应幂等，遇到已有保留记录不覆盖用户状态。

## 后端分层

### 应用层

在 `htht-multisource` 增加：

- `TaskHistoryQueryUseCase`：按 `AccessSubject` 分页查询历史摘要，支持产品编码、任务状态、完成时间、产物状态筛选，并汇总 `rs_artifact.size_bytes`。
- `TaskArtifactRetentionUseCase`：执行 `PIN`、`RECYCLE`、`RESTORE` 和发起 `PURGE`，所有操作先校验任务归属和合法状态转换；`RECYCLE` 清除 `pinned`，`RESTORE` 回到 `AVAILABLE`。
- `TaskArtifactRetentionRepository`：读写保留记录，使用 `version` 做乐观并发控制，防止重复清理任务互相覆盖。
- `TaskArtifactPurgePort`：抽象删除任务工作目录、最终输出目录、ZIP 缓存和栅格预览缓存。
- `TaskArtifactAccessGuard`：根据任务归属和保留状态判断是否允许产物访问，并返回稳定的业务错误码。

历史详情继续复用 `GetTaskDetailUseCase`，由控制器组合进度、产物、保留状态、总大小、回收/清理时间。

### 管理端适配器

在 `htht-admin` 增加本地存储实现：

- 解析 `artifactRoot`、`workRoot`、任务 output、`archives/{taskId}`、瓦片/PNG 缓存路径。
- 每个解析后的路径都 `toAbsolutePath().normalize()`，并验证位于配置根目录内。
- 删除前再次确认任务已归属当前用户、已终态且不是执行/重试中。
- 删除过程按路径逐项执行；文件不存在视为幂等成功，其他异常记录失败原因。
- 删除成功后让内存预览服务按 `taskId`/checksum 失效缓存；失败不得清除数据库任务记录。

异步执行可先使用受控线程池和数据库状态抢占：事务内将记录从 `RECYCLE_BIN` 或 `PURGE_FAILED` 更新为 `PURGING` 并写入 `purge_started_at`，提交后提交清理任务；进程重启时只把超过固定恢复阈值的遗留 `PURGING` 标记为 `PURGE_FAILED`，不隐式继续物理删除，等待用户或运维显式重试。第一期不启用自动过期扫描。

## 访问控制

所有历史接口按当前 `AccessSubject.userId` 过滤；详情、状态变更和永久删除必须再次检查任务创建者。不存在的任务和他人任务统一返回不可访问，避免枚举。

以下既有接口必须在进入文件读取、检查或 worker 调用前经过 `TaskArtifactAccessGuard`：

- 单文件下载。
- ZIP 下载票据签发和 ZIP 消费。
- artifact inspection。
- 图片预览票据后的图片输出。
- 栅格 tile-preview 和票据后的瓦片输出。

匿名图片/瓦片端点先解析短期票据，再根据票据中的 taskId/subject 查询状态；旧票据不能绕过 `RECYCLE_BIN`、`PURGING`、`PURGE_FAILED`、`PURGED` 或 `MISSING`。

错误响应至少包含稳定码：`ARTIFACT_RECYCLED`、`ARTIFACT_PURGING`、`ARTIFACT_PURGE_FAILED`、`ARTIFACT_PURGED`、`ARTIFACT_MISSING`，前端据此显示具体原因并禁用操作。

## HTTP 接口

- `GET /api/multisource/processing-tasks/history`：分页摘要和筛选。摘要包含任务编号/名称、产品、完成时间、生命周期状态、产物数量、估算占用、保留状态。
- `GET /api/multisource/processing-tasks/{taskId}/history-detail`：归属校验后返回复用的进度/产物详情、总大小、当前状态、回收时间、计划/实际清理时间和失败原因。
- `POST /api/multisource/processing-tasks/{taskId}/artifact-retention`：body 操作 `PIN`、`RECYCLE`、`RESTORE`；要求幂等，非法转换返回冲突。
- `POST /api/multisource/processing-tasks/{taskId}/artifact-purge`：body 必须带显式任务编号确认；只接受终态和当前用户任务，返回 `202 Accepted` 及当前清理状态。
- `GET /api/multisource/processing-storage/summary`：返回当前用户可用产物数量、回收站占用、估算总占用和缺失数量。

分页排序固定为 `updated_at DESC, task_id DESC`，避免同一时间戳导致翻页重复或遗漏。产品筛选使用 `rs_task_product.product_code`，不把旧计划封存状态混入产物状态。

## 前端设计

在启动卡片后新增独立小卡片，不依赖上传状态或当前任务执行状态。点击打开 `TaskHistoryDialog.vue`。

弹窗分为列表和详情区域：顶部筛选、分页任务表、点击展开详情。详情复用现有进度展示与产物动作，通过现有 API 函数打开质量报告、单文件下载、ZIP 下载、图片查看、地图和瓦片。

按钮状态由后端状态和错误码驱动：`AVAILABLE/PINNED` 可用；`RECYCLE_BIN/PURGING/PURGE_FAILED/PURGED/MISSING` 的查看和下载禁用，并显示具体原因。永久删除使用任务编号二次确认，操作提交后立即刷新状态并允许关闭弹窗。

`multiSourceSatellite.ts` 增加历史摘要、详情、保留变更、清理、存储汇总的类型和请求函数；不复制现有产物格式判断和下载 URL 生成逻辑。

## 一致性与失败处理

1. 状态变更使用事务和版本号；同一任务的重复点击必须幂等。
2. `PURGE` 先写 `PURGING` 再做物理删除，不能先删文件再写数据库。
3. 部分路径删除后失败，记录 `PURGE_FAILED` 和失败原因；重试继续按幂等删除执行。
4. 任务、阶段、事件、质量结果和产物元数据永不因物理清理而删除。
5. 清理过程中新发出的下载/预览请求必须被状态守卫拒绝；已开始的 HTTP 流无法撤回，状态切换只保证后续请求不可访问。
6. `MISSING` 只表示已登记但文件不存在，不自动恢复、不自动删除数据库记录。

## 测试与上线顺序

- 数据库：迁移结构、约束、索引、初始化扫描幂等性。
- 应用层：状态转换、终态/归属校验、并发版本冲突、错误码。
- 存储适配器：路径越界拒绝、目录/ZIP/瓦片缓存清理、重复清理、部分失败重试。
- 控制器：所有下载/预览/瓦片入口在回收站和清理状态下拒绝，跨用户任务不可枚举。
- 前端：筛选分页、展开详情、状态按钮禁用、二次确认、错误提示和恢复后刷新。
- 集成验收：跨页面重新进入可下载；移入回收站立即禁用 ZIP、图片、检查和瓦片；恢复后恢复访问；永久删除不影响原始上传文件；磁盘扫描结果与汇总占用一致。

上线分四步：先部署迁移和历史查询/只读详情；再启用下载状态校验；随后启用回收站/恢复；最后启用永久删除和失败重试。第一期不注册自动清理调度器。
