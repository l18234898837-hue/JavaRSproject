# 海洋多源卫星数据处理模块实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付海洋多源卫星数据处理后端模块的首个可运行闭环，先打通产品预检、任务编排、数据库迁移、Worker 契约和四类真实样例适配，再扩展到后续产品。

**Architecture:** Java 后端保持领域、应用、基础设施分层，`htht-multisource` 只承载领域模型、用例和端口，`htht-admin` 负责后端控制器与现有系统能力装配，独立 Python Worker 只通过固定 JSON 契约受控调用。首阶段先用本地 Worker Gateway 跑通真实样例和 `rs_` 迁移，再把远程 Worker、队列和更多产品适配器作为可替换实现；不开发前端页面，也不做前后端联调。

**Tech Stack:** Java 17, Spring Boot 2.7, MyBatis-Plus, PostgreSQL/PostGIS, JUnit 5, Maven, RuoYi-Vue-Plus 现有基础设施, Python 3, GDAL, pytest.

---

### Task 1: 同步总览文档并冻结首版范围

**Files:**
- Modify: `C:\Users\24257\Desktop\GIS开发\海洋多源卫星数据处理系统开发计划.md`
- Modify: `C:\Users\24257\Desktop\GIS开发\新会话开发交接上下文.md`

- [ ] **Step 1: 在总开发计划里增加详细计划索引**

将 `海洋多源卫星数据处理系统开发计划.md` 的结尾补成两层结构：上层保留阶段总览，新增 `## 22. 详细实施计划`，明确指出细化任务已经下沉到 `JavaRSproject/docs/superpowers/plans/2026-08-18-multisource-implementation-plan.md`，以后以该文件作为实际执行基线。

- [ ] **Step 2: 在交接文档里加入新计划入口**

把 `新会话开发交接上下文.md` 的“新会话启动动作”第 3 条加一项，要求新会话在读完架构设计和开发计划后，再读取 `JavaRSproject/docs/superpowers/plans/2026-08-18-multisource-implementation-plan.md`，避免后续会话只看到总览而看不到任务级拆分。

- [ ] **Step 3: 统一文档中的阶段边界表述**

检查两份中文文档里关于“阶段 0/1/2”和“首批四类样例”的表述，确保它们都一致指向同一批对象：FY-3H MERSI、HY-1C CZI、FY-4B AGRI、HY-2D 高度计；CFOSAT、GF、SAR、SPOT 继续保持为“仅预检和候选，不自动进入生产处理”。

- [ ] **Step 4: 统一后端范围表述**

检查两份中文文档里关于前端、页面和联调的内容，统一标注为“本阶段不实施”。本阶段交付只包括 Java 后端、数据库迁移、Worker 契约、Worker 最小适配器和后端 API 自动化测试；不创建 Vue 页面，不配置前端路由菜单，不做前端调用验收。

- [ ] **Step 5: 验证文档链接与路径**

运行：

```powershell
rg -n "multisource-implementation-plan|详细实施计划" "C:\Users\24257\Desktop\GIS开发\海洋多源卫星数据处理系统开发计划.md" "C:\Users\24257\Desktop\GIS开发\新会话开发交接上下文.md"
```

Expected: 两个文件都能找到新的计划入口文字，且没有残留旧的“只看总览就能开工”或“本阶段要实施前端页面”的说法。

- [ ] **Step 6: 提交文档同步改动**

`海洋多源卫星数据处理系统开发计划.md` 和 `新会话开发交接上下文.md` 位于 `JavaRSproject` 仓库外，只保存为工作区文档同步结果；Git 提交只包含仓库内的详细计划文件。

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject"
git add "docs\superpowers\plans\2026-08-18-multisource-implementation-plan.md"
git commit -m "docs: add detailed multisource implementation plan"
```

---

### Task 2: 扩展领域契约和产品候选模型

**Files:**
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProductCandidate.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProductPackage.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProductProfile.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProductProfileKey.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProductSupportStatus.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProductDataShape.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProcessingBatch.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProcessingBatchStatus.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\InspectionResult.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProcessingTask.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProcessingPlan.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProcessingStage.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\InputAsset.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\Artifact.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\TaskStage.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model\ProcessingPlanValidator.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\domain\model\ProcessingPlanValidatorTest.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\domain\model\ProductCandidateTest.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\domain\model\ProcessingTaskTest.java`

- [x] **Step 1: 先写候选产品与计划校验的失败测试**

先写这组测试，确保模型不会被“单文件单产品”的假设污染：

```java
@Test
void candidateListMustBeCopiedAndVisible() {
    ProductCandidate candidate = new ProductCandidate(
        "fy3h-mersi-granule-01",
        "FY-3H-MERSI",
        ProductDataShape.SWATH_RASTER,
        ProductSupportStatus.CANDIDATE,
        List.of(mainFile, metadataFile),
        0.87
    );
    assertThat(candidate.files()).containsExactly(mainFile, metadataFile);
}
```

```java
@Test
void rejectPlanWithCycle() {
    ProcessingStage a = new ProcessingStage("A", "READ", List.of("B"));
    ProcessingStage b = new ProcessingStage("B", "CAL", List.of("A"));
    assertThatThrownBy(() -> new ProcessingPlan("v1", List.of(a, b)))
        .isInstanceOf(IllegalArgumentException.class);
}
```

- [x] **Step 2: 运行 domain 测试并确认先失败**

运行：

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject"
mvn -pl htht-modules\htht-multisource -am -Dtest=ProcessingPlanValidatorTest,ProductCandidateTest,ProcessingTaskTest test
```

Expected: 新测试先失败，失败点集中在缺失的候选模型、批次模型或计划字段，而不是编译期乱码。

- [x] **Step 3: 实现最小领域模型**

先把枚举、record 和聚合壳子补齐，再把现有 `ProcessingTask`、`InspectionResult`、`ProcessingStage` 等类改成支持“多候选、批次、状态分离、产品形态显式建模”的形式；禁止用 `type` 字符串承载全部语义。

- [x] **Step 4: 运行测试并把失败面收窄到零**

再次运行同一组 `mvn test` 命令，直到 `domain/model` 的测试全部通过，并且构造函数对空值、空白值、循环依赖和无效置信度都能准确报错。

- [x] **Step 5: 提交领域契约改动**

```powershell
git add "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\domain\model" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\domain\model"
git commit -m "feat: expand multisource domain contracts"
```

---

### Task 3: 扩展应用层用例和出站端口

**Files:**
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\CreateProcessingBatchUseCase.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\GetTaskDetailUseCase.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\DispatchNextStageUseCase.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\ReceiveWorkerEventUseCase.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\RecoverExpiredStageUseCase.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\CreateProcessingTaskUseCase.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\CreateProcessingTaskCommand.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\PreflightProductUseCase.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\CancelProcessingTaskUseCase.java`
- Modify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\RetryFailedStageUseCase.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\model\TaskDetailView.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\model\TaskStageView.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\model\TaskArtifactView.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\model\BatchCreateCommand.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\service\PreflightProductService.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\service\CreateProcessingTaskService.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\service\CreateProcessingBatchService.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\service\GetTaskDetailService.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\service\DispatchNextStageService.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\service\ReceiveWorkerEventService.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application\usecase\service\RecoverExpiredStageService.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\application\usecase\CreateProcessingTaskUseCaseTest.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\application\usecase\PreflightProductUseCaseTest.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\application\usecase\GetTaskDetailUseCaseTest.java`

- [ ] **Step 1: 先写用例测试，覆盖预检、创建和查询**

把用例测试写成纯 Java 单元测试，不先碰数据库。每个测试都用内存测试替身验证：预检必须返回候选列表，创建任务必须保存计划，任务详情必须把阶段和产物拼出来。

```java
@Test
void createTaskPersistsPlanAndReturnsTaskId() {
    InMemoryProcessingTaskRepository tasks = new InMemoryProcessingTaskRepository();
    FixedProcessingPlanner planner = new FixedProcessingPlanner(samplePlan());
    CreateProcessingTaskService useCase = new CreateProcessingTaskService(
        tasks,
        planner,
        new InMemoryTaskStageRepository(),
        new InMemoryArtifactRepository()
    );

    TaskId taskId = useCase.create(commandWithCandidate("FY-3H-MERSI"));

    assertThat(taskId.value()).isPositive();
    assertThat(tasks.savedTasks()).hasSize(1);
    assertThat(tasks.savedTasks().get(0).plan()).isEqualTo(samplePlan());
}
```

- [ ] **Step 2: 运行应用层测试并确认接口空缺**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject"
mvn -pl htht-modules\htht-multisource -am -Dtest=CreateProcessingTaskUseCaseTest,PreflightProductUseCaseTest,GetTaskDetailUseCaseTest test
```

Expected: 测试先失败在缺失的用例接口或返回模型，而不是业务逻辑已经偷偷耦合了实现细节。

- [ ] **Step 3: 实现应用层编排**

把创建任务、批次创建、取消、重试、恢复和 Worker 回调都收敛到应用层，事务边界只围绕一条任务链路，不把 SQL、HTTP、ProcessBuilder 或 GDAL 引进用例层。

- [ ] **Step 4: 重新跑测试并收口**

重复上面的 `mvn test` 命令，直到用例层测试通过，并且 `CreateProcessingTaskCommand`、`TaskDetailView`、`TaskStageView` 的字段命名和领域对象一致。

- [ ] **Step 5: 提交应用层改动**

```powershell
git add "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\application" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\application"
git commit -m "feat: expand multisource application layer"
```

---

### Task 4: 落地数据库迁移和仓储骨架

**Files:**
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\script\sql\migrations\V20260818_01__create_multisource_core_tables.sql`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\script\sql\migrations\V20260818_02__create_multisource_indexes.sql`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\script\sql\migrations\V20260818_03__seed_multisource_profiles.sql`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\entity\ProcessingTaskEntity.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\entity\TaskStageEntity.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\entity\TaskProductEntity.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\entity\ProcessingBatchEntity.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\mapper\ProcessingTaskMapper.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\mapper\TaskStageMapper.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\mapper\TaskProductMapper.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\mapper\ProcessingBatchMapper.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\repository\MyBatisProcessingTaskRepository.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\repository\MyBatisTaskStageRepository.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence\repository\MyBatisArtifactRepository.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\infrastructure\persistence\MigrationScriptTest.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\infrastructure\persistence\ProcessingTaskRepositoryIT.java`

- [ ] **Step 1: 先把 SQL 脚本写成可审计版本**

`V20260818_01__create_multisource_core_tables.sql` 负责表结构和外键，`V20260818_02__create_multisource_indexes.sql` 负责索引，`V20260818_03__seed_multisource_profiles.sql` 负责首批 profile 和 capability 种子数据。每个脚本都要在注释头写明目的、依赖和回滚提醒。

- [ ] **Step 2: 先在开发库试跑迁移脚本**

运行：

```powershell
psql -h 127.0.0.1 -p 5433 -U htht_rs_dev -d htht_rs_dev -f "C:\Users\24257\Desktop\GIS开发\JavaRSproject\script\sql\migrations\V20260818_01__create_multisource_core_tables.sql"
psql -h 127.0.0.1 -p 5433 -U htht_rs_dev -d htht_rs_dev -f "C:\Users\24257\Desktop\GIS开发\JavaRSproject\script\sql\migrations\V20260818_02__create_multisource_indexes.sql"
psql -h 127.0.0.1 -p 5433 -U htht_rs_dev -d htht_rs_dev -f "C:\Users\24257\Desktop\GIS开发\JavaRSproject\script\sql\migrations\V20260818_03__seed_multisource_profiles.sql"
```

Expected: `rs_processing_task`、`rs_task_stage`、`rs_task_product`、`rs_processing_batch`、`rs_schema_migration` 和必要索引全部出现，且 `\d` 查看结果符合文档约束。

- [ ] **Step 3: 实现仓储映射**

把 `ProcessingTaskRepository`、`TaskStageRepository`、`ArtifactRepository` 对应到新的 `mapper` 和 `entity`，只写 `rs_` 表，不碰旧业务表；用户关联、文件关联和批次关联都通过显式字段和快照保存。

- [ ] **Step 4: 运行仓储和迁移验证**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject"
mvn -pl htht-modules\htht-multisource -am -Dtest=MigrationScriptTest,ProcessingTaskRepositoryIT test
```

Expected: 迁移脚本能重复检查版本，仓储读写能回环，且不会引用 `bus_data_info`、`sd_algorithm_run_record` 或 `st_algorithm_record`。

- [ ] **Step 5: 提交数据库和仓储改动**

```powershell
git add "C:\Users\24257\Desktop\GIS开发\JavaRSproject\script\sql\migrations" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\persistence" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\infrastructure\persistence"
git commit -m "feat: add multisource database migrations and repositories"
```

---

### Task 5: 落地 Admin 装配层和后端 HTTP API

**Files:**
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\config\multisource\MultiSourceAdapterConfiguration.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\config\multisource\SysFileAssetStorageAdapter.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\config\multisource\LegacyAlgorithmAdapter.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\web\controller\multisource\ProcessingTaskController.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\web\controller\multisource\ProcessingBatchController.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\web\controller\multisource\ProductProfileController.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\web\controller\multisource\dto\CreateProcessingTaskBody.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\web\controller\multisource\dto\PreflightBody.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\web\controller\multisource\dto\ProcessingTaskDetailVo.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\api\dto\PreflightResponse.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\api\dto\ProcessingTaskSummaryVo.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\test\java\com\htht\web\controller\multisource\ProcessingTaskControllerTest.java`

- [ ] **Step 1: 先写控制器测试**

用 `@WebMvcTest` 或等价的轻量 Web 测试验证三件事：`/processing-tasks/preflight` 能返回候选列表，`/processing-tasks` 能创建任务，`/processing-tasks/{id}` 能返回任务详情；不要在测试里直接碰数据库。

```java
@Test
void preflightReturnsCandidates() throws Exception {
    mockMvc.perform(post("/processing-tasks/preflight").content(json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.candidates").isArray());
}
```

- [ ] **Step 2: 运行控制器测试并确认装配缺口**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject"
mvn -pl htht-admin -am -Dtest=ProcessingTaskControllerTest test
```

Expected: 先失败在缺少控制器、DTO、装配类或端口绑定，而不是 HTTP 路由已经返回假数据。

- [ ] **Step 3: 实现装配层和后端 HTTP 适配器**

在 `com.htht.config.multisource` 里把 `ISysFileService`、旧文件能力和 `AssetStorage` 端口装配起来，在 `com.htht.web.controller.multisource` 里只保留 HTTP 与 DTO 的转换，不把业务流程写进 Controller。本任务只交付后端 API 和控制器测试，不创建或修改 Vue 页面。

- [ ] **Step 4: 重新跑控制器测试**

再次执行上面的 `mvn test` 命令，直到控制器层只负责入参校验、调用用例和响应包装，且不含任何路径拼接、进程启动或 SQL 拼接。

- [ ] **Step 5: 提交 API 和装配层改动**

```powershell
git add "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\config\multisource" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\main\java\com\htht\web\controller\multisource" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\test\java\com\htht\web\controller\multisource" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\api"
git commit -m "feat: add multisource http api and adapters"
```

---

### Task 6: 实现本地 Worker Gateway 和受控事件流

**Files:**
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\worker\LocalProcessWorkerGateway.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\worker\LocalProcessWorkerProperties.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\worker\WorkerContractCodec.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\worker\WorkerResultTranslator.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\worker\WorkerProcessLauncher.java`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\resources\worker\inspect-request-template.json`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\resources\worker\plan-request-template.json`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\resources\worker\stage-request-template.json`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\infrastructure\worker\LocalProcessWorkerGatewayTest.java`
- Test: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\infrastructure\worker\WorkerContractCodecTest.java`

- [ ] **Step 1: 先写契约编码和网关测试**

测试重点是三类请求能否按固定版本 JSON 编码，以及 Worker 返回的事件、产物和校验值能否被译回领域对象。

```java
@Test
void codecWritesVersionedInspectRequest() {
    String json = codec.encodeInspect(request);
    assertThat(json).contains("\"schemaVersion\"");
    assertThat(json).contains("\"taskId\"");
}
```

- [ ] **Step 2: 运行网关测试并观察契约缺失**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject"
mvn -pl htht-modules\htht-multisource -am -Dtest=LocalProcessWorkerGatewayTest,WorkerContractCodecTest test
```

Expected: 先失败在缺少网关、编码器或模板资源，而不是本地进程调用已经悄悄走通但没有契约约束。

- [ ] **Step 3: 实现最小网关**

本地网关只做三件事：写请求 JSON、启动受控进程、收回结构化响应；它不直接承担任务状态机，不吞掉 Worker 错误码，也不把 stdout 当成业务真相。

- [ ] **Step 4: 重新跑网关测试**

再次执行同一组 Maven 测试，直到 `dispatch`、`cancel` 和事件翻译都能在本地稳定工作，并且请求模板、输出模板和错误码映射都可重复。

- [ ] **Step 5: 提交 Worker 网关改动**

```powershell
git add "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\java\com\htht\multisource\infrastructure\worker" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\main\resources\worker" "C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\infrastructure\worker"
git commit -m "feat: add multisource local worker gateway"
```

---

### Task 7: 落地四类首批产品适配器

**Files:**
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\pyproject.toml`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\src\rs_algorithm_worker\__init__.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\src\rs_algorithm_worker\contracts.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\src\product_adapters\fy3h_mersi.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\src\product_adapters\hy1c_czi.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\src\product_adapters\fy4b_agri.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\src\product_adapters\hy2d_alt.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\src\quality\__init__.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_fy3h_mersi.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_hy1c_czi.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_fy4b_agri.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_hy2d_alt.py`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\fixtures\fy3h_mersi\inspect-response.json`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\fixtures\hy1c_czi\inspect-response.json`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\fixtures\fy4b_agri\inspect-response.json`
- Create: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\fixtures\hy2d_alt\inspect-response.json`

- [ ] **Step 1: 先把四类样例的 inspect 测试写出来**

每个测试只验证一件事：样例能否被识别为正确的数据形态，并返回候选产品、元数据摘要和规则包版本。FY-3H MERSI 和 HY-1C CZI 要返回 `SWATH_RASTER`，FY-4B AGRI 返回 `GEOSTATIONARY_RASTER`，HY-2D 高度计返回 `ALONG_TRACK_OBSERVATION`。

- [ ] **Step 2: 在 Python 侧跑失败测试**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker"
pytest -q
```

Expected: 先失败在缺少适配器、契约模型或 fixture，而不是识别结果已经错误落地。

- [ ] **Step 3: 实现最小适配器**

先实现 `inspect` 和 `build-plan`，只做识别、元数据抽取和计划草图，不做生产级算法；`execute-stage` 先只支持最小闭环，确保 Java 契约能接住真实样例返回。

- [ ] **Step 4: 再跑 Python 测试**

继续 `pytest -q`，直到四类样例的契约测试通过，且输出字段和 Java 端的 `InspectionResult`、`ProcessingPlan`、`DispatchReceipt` 一一对应。

- [ ] **Step 5: 提交四类首批适配器**

```powershell
git add "C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker"
git commit -m "feat: add first multisource worker adapters"
```

---

### Task 8: 做后端全链路验证和回归收口

**Files:**
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\domain\model\ProcessingPlanValidatorTest.java`
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\application\usecase\CreateProcessingTaskUseCaseTest.java`
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-modules\htht-multisource\src\test\java\com\htht\multisource\infrastructure\worker\LocalProcessWorkerGatewayTest.java`
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\htht-admin\src\test\java\com\htht\web\controller\multisource\ProcessingTaskControllerTest.java`
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_fy3h_mersi.py`
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_hy1c_czi.py`
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_fy4b_agri.py`
- Verify: `C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker\tests\test_hy2d_alt.py`

- [ ] **Step 1: 跑 Maven 单元和后端集成测试**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject"
mvn -pl htht-modules\htht-multisource,htht-admin -am test
```

Expected: Java 端的领域、用例、仓储、后端控制器和网关测试全部通过，不包含前端页面和前后端联调测试。

- [ ] **Step 2: 跑 Python 适配器测试**

```powershell
cd "C:\Users\24257\Desktop\GIS开发\JavaRSproject\rs-algorithm-worker"
pytest -q
```

Expected: 四类样例的 inspect 契约和最小计划契约全部通过。

- [ ] **Step 3: 做开发库回归检查**

在 `htht_rs_dev` 上确认新表、索引和迁移记录都在，旧表没有被写坏；同时验证新模块没有引入 `bus_data_info` 依赖，也没有把旧算法运行记录拿来当任务主状态。

- [ ] **Step 4: 做一次代码审查式自检**

检查三件事：跨层依赖是否干净、事务边界是否合理、失败与重试是否可恢复。把所有“先能跑再说”的临时做法清掉，尤其是绝对路径、stdout 状态源和硬编码产品类型。

- [ ] **Step 5: 做最终提交和发布标记**

```powershell
git status --short
git add -A
git commit -m "feat: deliver multisource foundation slice"
```

---

### Execution Notes

- 先跑 Task 1 到 Task 4，形成 Java 侧的稳定边界。
- Task 5 和 Task 6 可在 Task 4 完成后并行推进。
- Task 7 依赖 Task 6 的契约格式，但可以先用固定 fixture 开工。
- 本计划不实施前端页面、菜单、路由和前后端联调；后端 HTTP API 仅通过控制器测试和契约测试验收。
- 任何时候都不要把 `htht-multisource` 直接耦合到 `htht-system`、`htht-business` 或 `htht-algorithm` 的内部实现。
- 未经样例和官方规范验证的产品，只能停留在 `CANDIDATE` / `UNKNOWN_PRODUCT`，不能自动进入生产处理。
