# 多源处理产物与进度联动验收记录

## 本次实现

- `/progress` 增加可选阶段耗时、执行时间、职责、声明输出字段。
- `/progress` 增加可选产物大小、保留级别、媒体类型、预览能力和显示名称字段。
- 前端按阶段完成边沿自动展开当前阶段产物区。
- 阶段行展示耗时和产物数量；产物区展示分类、大小和保留级别。
- 保留现有安全下载、图片预览、最终 ZIP、取消和重试行为。

## 自动化验证

| 检查 | 结果 |
| --- | --- |
| `pnpm test:multisource` | 199/199 通过 |
| `mvn -pl htht-modules/htht-multisource -am "-Dtest=DispatchNextStageUseCaseTest,AcceptWorkerEventUseCaseTest,WorkerContractCodecTest" "-DfailIfNoTests=false" test` | 30/30 通过 |
| `mvn -pl htht-admin -am -DskipTests compile` | 构建通过 |
| `pnpm typecheck` | 受仓库既有 ReAuth/Cesium/Mapbox/Axios 类型错误阻塞 |

## 兼容性说明

- 新增 API 字段均为可选字段；旧任务和旧前端缺少字段时使用稳定回退。
- 大型 COG、NetCDF、Parquet、HDF 产物仍走授权下载，不直接暴露物理路径。
- 当前自动验收环境未连接真实五类产品任务，因此真实 taskId、阶段耗时和浏览器预览结果需要在运行中的后端环境补录。
- 管理端完整测试命令会在无关的 reactor 模块中因既有 surefire/TestNG 配置失败；多源模块定向测试和管理端编译均通过。
