# HY-2D 测高沿轨基线处理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 让 HY-2D IDR 2MC 压缩包在现有系统中完成解析、质量过滤、SSH 基线计算、沿轨特征和异常检测、标准输出及可选统计网格化。

**Architecture:** 复用 Java 任务、DAG、Worker 协议和产物表，先补齐阶段声明驱动的最小输入授权与正式产物提升。Worker 为 HY-2D 建立一维沿轨数据模型和 NetCDF 处理链；不复用影像融合和图像显著性算法。

**Tech Stack:** Java 17、Spring Boot、MyBatis、PostgreSQL、Python 3.11、NumPy、netCDF4、SciPy、PyTest、JUnit 5。

---

## 范围和科学边界

- 输入仅支持 HY-2D IDR 2MC .tar.gz 包，包内必须有一个 .nc 和一个 .meta.xml。
- 产物名称为“HY-2D 沿轨基线质控产品”。官方 IDR/IGDR 详细规范尚未获得时，质量位、改正项符号和阈值只能作为版本化基线规则，不能称为官方科学标准。
- fusion 不适用，后续单建“多源测高融合”。saliency 不适用，替换为本计划中的沿轨异常检测。
- 网格化仅对有效沿轨观测做格网统计，不做插值，不称为卫星影像投影产品。

## HY-2D 阶段与产物契约

| 阶段 | 输入 | 输出 |
| --- | --- | --- |
| VALIDATE_SOURCE | MAIN | VALIDATION_METADATA_JSON |
| DECODE_ALTIMETER_TRACK | MAIN | DECODED_ALTIMETER_TRACK |
| APPLY_TRACK_QUALITY | DECODED_ALTIMETER_TRACK | QUALITY_FILTERED_ALTIMETER_TRACK、TRACK_QUALITY_SUMMARY_JSON |
| CALCULATE_BASELINE_SSH | QUALITY_FILTERED_ALTIMETER_TRACK | BASELINE_SSH_TRACK |
| EXTRACT_TRACK_FEATURES | BASELINE_SSH_TRACK | TRACK_FEATURES_JSON、TRACK_FEATURE_WINDOWS_PARQUET |
| DETECT_TRACK_ANOMALIES | BASELINE_SSH_TRACK | TRACK_ANOMALIES_GEOJSON、TRACK_ANOMALY_SUMMARY_JSON |
| WRITE_STANDARD_TRACK_OUTPUT | SSH、质量、特征、异常 | STANDARD_TRACK_NETCDF、STANDARD_TRACK_PARQUET、STANDARD_TRACK_CSV、STANDARD_TRACK_GEOJSON、STANDARD_TRACK_SUMMARY_JSON |
| GRID_TRACK_OPTIONAL | BASELINE_SSH_TRACK | TRACK_GRID_NETCDF、TRACK_GRID_COG、TRACK_GRID_SUMMARY_JSON |

GRID_TRACK_OPTIONAL 仅在 parameters.grid.enabled=true 时生成。所有阶段都使用同一冻结的 parameterSnapshot。

## 文件结构

### Java

- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/StageInputResolver.java
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskArtifactStore.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/LocalTaskArtifactStore.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/CompositeLocalWorkerAssetResolver.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/ArtifactRegistrationService.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/LocalWorkerAssetManifestWriter.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/SysFileAssetStorageAdapter.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/domain/model/Artifact.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/entity/ArtifactEntity.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/persistence/mapping/TaskPersistenceMapper.java
- Create: htht-modules/htht-multisource/src/main/resources/db/migration/V20260825__add_artifact_storage_key.sql
- Modify: htht-admin/src/main/java/com/htht/config/multisource/LocalWorkerResultArchiveService.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java

### Python Worker

- Modify: rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy2d_altimeter.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/models_track.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/hy2d_pipeline.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/netcdf_track.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/track_quality.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/ssh.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/track_grid.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/track_output.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/algorithms/track_features.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/algorithms/track_anomaly.py
- Modify: rs-algorithm-worker/src/rs_algorithm_worker/registry.py
- Modify: rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py

### Tests

- Create: htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/StageInputResolverTest.java
- Create: htht-admin/src/test/java/com/htht/config/multisource/LocalTaskArtifactStoreTest.java
- Modify: htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java
- Create: rs-algorithm-worker/tests/test_hy2d_pipeline.py
- Create: rs-algorithm-worker/tests/test_track_quality.py
- Create: rs-algorithm-worker/tests/test_ssh.py
- Create: rs-algorithm-worker/tests/test_track_features.py
- Create: rs-algorithm-worker/tests/test_track_anomaly.py
- Create: rs-algorithm-worker/tests/test_track_grid.py

## 实施任务

### Task 1: 建立阶段输入解析器

**Files:**
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/StageInputResolver.java
- Test: htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/StageInputResolverTest.java

- [ ] **Step 1: 写出未声明依赖的上游产物必须被拒绝的失败测试**

~~~java
@Test
void rejectsAnArtifactThatIsNotProducedByADependency() {
    ProcessingStage stage = new ProcessingStage("WRITE", "WRITE", List.of(),
        List.of(new StageInput("decoded", StageInput.Source.UPSTREAM_ARTIFACT, "DECODED_TRACK", true)),
        List.of(), "{}");
    assertThatThrownBy(() -> resolver.resolve(task, stage, artifacts))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declared dependency");
}
~~~

- [ ] **Step 2: 运行测试，确认失败**

Run: mvn -pl htht-modules/htht-multisource -Dtest=StageInputResolverTest test

Expected: 编译失败，提示 StageInputResolver 不存在。

- [ ] **Step 3: 实现解析规则**

~~~java
public final class StageInputResolver {
    public List<InputAsset> resolve(ProcessingTask task, ProcessingStage stage,
                                    List<Artifact> artifacts) {
        // RAW_INPUT：candidate.files() 中 role == input.artifactType() 的唯一文件。
        // UPSTREAM_ARTIFACT：仅匹配 dependency stage 产生的同 type Artifact。
        // required=true 必须唯一命中；0 或多个命中均抛 IllegalArgumentException。
    }
}
~~~

上游产物转换成 InputAsset 时，role 固定为 StageInput.key()，引用类型为 TASK_ARTIFACT，storage URI 使用已登记的产物 URI；原始资产保持原始 role 和 ordinal。

- [ ] **Step 4: 补充 RAW_INPUT、可选输入、零命中和多命中测试并运行**

Run: mvn -pl htht-modules/htht-multisource -Dtest=StageInputResolverTest test

Expected: PASS。

- [ ] **Step 5: 提交**

~~~bash
git add htht-modules/htht-multisource/src/main htht-modules/htht-multisource/src/test
git commit -m "feat: resolve declared processing stage inputs"
~~~

### Task 2: 将 Worker 产物提升到任务正式目录

**Files:**
- Create: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/port/out/TaskArtifactStore.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/LocalTaskArtifactStore.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/ArtifactRegistrationService.java
- Test: htht-admin/src/test/java/com/htht/config/multisource/LocalTaskArtifactStoreTest.java

- [ ] **Step 1: 写出临时执行目录删除后仍能读取正式产物的失败测试**

~~~java
@Test
void promotesWorkerOutputBeforeTheExecutionDirectoryIsRemoved() throws Exception {
    Artifact artifact = store.materialize(taskId, stageId, executionId, outputUri, candidate, output);
    Files.delete(workerOutput);
    assertThat(Files.readString(store.resolveForDownload(artifact))).isEqualTo("result");
}
~~~

- [ ] **Step 2: 运行测试，确认失败**

Run: mvn -pl htht-admin -Dtest=LocalTaskArtifactStoreTest test

Expected: 编译失败，提示 LocalTaskArtifactStore 不存在。

- [ ] **Step 3: 定义端口并实现原子提升**

~~~java
public interface TaskArtifactStore {
    Artifact materialize(TaskId taskId, StageId stageId, ExecutionId executionId,
                         StorageUri outputUri, ArtifactCandidate candidate, StageOutput output);
    InputAsset asInput(Artifact artifact, String inputKey);
    Path resolveForDownload(Artifact artifact);
}
~~~

为 Artifact 和 rs_artifact 增加非空 storageKey 字段，值是相对 artifact-root 的受控路径，不能是绝对路径，也不能含有 . 或 .. 段。中间产物目录固定为 <artifact-root>/<task-id>/work/<stage-key>/<attempt>/<relative-path>，最终产物固定为 <artifact-root>/<task-id>/output/<relative-path>。storageKey 保存该正式相对路径，storageUri 继续保存面向业务的 rs://output URI。先复制到相同目录中的随机临时文件，复核 SHA-256，再使用 ATOMIC_MOVE；文件系统不支持时使用 REPLACE_EXISTING。

- [ ] **Step 4: 收紧登记验证**

ArtifactRegistrationService 在调用 materialize 前必须验证候选的 type 和 URI 相对路径恰好等于本阶段某个 StageOutput，并拒绝未声明、重复、缺失或校验和不匹配的产物。

- [ ] **Step 5: 运行测试并提交**

Run: mvn -pl htht-admin -Dtest=LocalTaskArtifactStoreTest test

Expected: PASS。

~~~bash
git add htht-admin htht-modules/htht-multisource
git commit -m "feat: promote worker artifacts to task storage"
~~~

### Task 3: 使用阶段声明生成 Worker manifest

**Files:**
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/application/usecase/service/DispatchNextStageService.java
- Modify: htht-modules/htht-multisource/src/main/java/com/htht/multisource/infrastructure/worker/LocalWorkerAssetManifestWriter.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/SysFileAssetStorageAdapter.java
- Create: htht-admin/src/main/java/com/htht/config/multisource/CompositeLocalWorkerAssetResolver.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/LocalWorkerResultArchiveService.java
- Modify: htht-admin/src/main/java/com/htht/config/multisource/MultiSourceAdapterConfiguration.java
- Test: htht-modules/htht-multisource/src/test/java/com/htht/multisource/application/usecase/DispatchNextStageUseCaseTest.java

- [ ] **Step 1: 写出质量阶段只获得解码产物而非原始压缩包的失败测试**

~~~java
assertThat(dispatchRequest.inputs()).extracting(InputAsset::role).containsExactly("decoded");
assertThat(dispatchRequest.inputs()).extracting(InputAsset::storageUriSnapshot)
    .doesNotContain(rawTarUri);
~~~

- [ ] **Step 2: 运行测试，确认当前实现错误地传入 candidate.files()**

Run: mvn -pl htht-modules/htht-multisource -Dtest=DispatchNextStageUseCaseTest test

Expected: FAIL，实际输入含原始文件。

- [ ] **Step 3: 接入 resolver、artifact store 和 manifest writer**

~~~java
List<Artifact> artifacts = artifactRepository.findByTaskId(task.id());
List<InputAsset> stageInputs = stageInputResolver.resolve(task, planStage, artifacts);
ExecutionGrant grant = assetStorage.grant(new ExecutionGrantRequest(
    task.id(), claim.stage().id(), executionId, claim.stage().attempt(), stageInputs, task.outputUri()));
~~~

manifest 只写入 stageInputs 的真实绝对路径、角色、序号、storage URI 和 SHA-256。TASK_ARTIFACT 由 TaskArtifactStore 解析为正式文件，LOCAL_FILE 保持现有 SysFileAssetStorageAdapter 解析路径。

CompositeLocalWorkerAssetResolver 必须先调用 SysFileAssetStorageAdapter.requireAuthorized(grantReference, storageUri)，再按 URI 前缀分派：rs://sys-file/ 交给原 SysFile resolver，rs://task-artifact/ 交给 TaskArtifactStore.resolveStorageKey。StageInputResolver 为上游 Artifact 构造的 storage URI 统一为 rs://task-artifact/<storageKey>；TaskArtifactStore 只接受这一前缀和已保存的安全 storageKey。这样即使猜到其他任务路径，也因不在本次 ExecutionGrant 范围内而无法读取。

- [ ] **Step 4: 从正式产物库提供下载**

~~~java
Path file = artifactStore.resolveForDownload(artifact);
if (!Files.isRegularFile(file) || !checksum(file).equalsIgnoreCase(artifact.checksum())) {
    throw new IOException("Registered artifact file is unavailable.");
}
~~~

删除 LocalWorkerResultArchiveService 中扫描 workRoot 的逻辑。

- [ ] **Step 5: 运行测试并提交**

Run: mvn -pl htht-modules/htht-multisource,htht-admin test

Expected: PASS。

~~~bash
git add htht-modules/htht-multisource htht-admin
git commit -m "feat: authorize worker inputs per processing stage"
~~~

### Task 4: 建立 HY-2D 一维轨迹模型和安全 NetCDF 解码

**Files:**
- Create: rs-algorithm-worker/src/rs_algorithm_worker/models_track.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/netcdf_track.py
- Create: rs-algorithm-worker/tests/test_hy2d_pipeline.py

- [ ] **Step 1: 写出比例因子、偏移量和 FillValue 解码的失败测试**

~~~python
def test_decode_variable_applies_scale_offset_and_fill_value():
    raw = np.array([100, -32768], dtype=np.int16)
    decoded = decode_values(raw, scale_factor=0.01, add_offset=1.0, fill_value=-32768)
    np.testing.assert_allclose(decoded[:1], [2.0])
    assert np.isnan(decoded[1])
~~~

- [ ] **Step 2: 运行测试，确认失败**

Run: pytest tests/test_hy2d_pipeline.py::test_decode_variable_applies_scale_offset_and_fill_value -v

Expected: FAIL，提示 netcdf_track 模块不存在。

- [ ] **Step 3: 实现 TrackData、解压和变量读取**

~~~python
@dataclass(frozen=True)
class TrackData:
    time: np.ndarray
    longitude: np.ndarray
    latitude: np.ndarray
    fields: Mapping[str, np.ndarray]
    quality: np.ndarray
    metadata: Mapping[str, Any]
~~~

安全解压时拒绝绝对路径、..、多个 .nc、缺少 .meta.xml、非 NETCDF3_CLASSIC。读取 lat、lon、alt、range_ku、必要改正项和质量字段；每个变量都经过 scale_factor、add_offset、_FillValue 解码。核心 1 Hz 数据长度必须一致，不一致即失败。

- [ ] **Step 4: 增加合成 NetCDF fixture 和真实样例只读冒烟测试**

Run: pytest tests/test_hy2d_pipeline.py -v

Expected: 合成测试 PASS；MULTISOURCE_HY2D_SAMPLE_ROOT 未配置时真实样例为 SKIPPED。

- [ ] **Step 5: 提交**

~~~bash
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: decode HY-2D altimeter track packages"
~~~

### Task 5: 实现版本化基线质量过滤和 SSH

**Files:**
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/track_quality.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/ssh.py
- Create: rs-algorithm-worker/tests/test_track_quality.py
- Create: rs-algorithm-worker/tests/test_ssh.py

- [ ] **Step 1: 写出质量掩膜与 SSH 计算的失败测试**

~~~python
def test_quality_marks_land_rain_and_missing_range_invalid():
    result = apply_track_quality(track, QualityPolicy.baseline())
    assert result.status.tolist() == [QualityStatus.VALID, QualityStatus.INVALID, QualityStatus.INVALID]

def test_baseline_ssh_uses_declared_corrections_and_tides():
    values = calculate_baseline_ssh(track)
    np.testing.assert_allclose(values, [700000.0 - (699990.0 + 2.0) - 3.0])
~~~

- [ ] **Step 2: 运行测试，确认失败**

Run: pytest tests/test_track_quality.py tests/test_ssh.py -v

Expected: FAIL，提示模块不存在。

- [ ] **Step 3: 实现保守质量策略**

QualityPolicy.baseline() 固定版本名 hy2d-idr-2mc-baseline-v1。仅将下列观测判为 INVALID：坐标或核心变量非有限值、surface_type 明确非海面、rain_flag 为真、ice_flag 为真、qual_alt_1hz_range_ku 非零、orb_state_flag 非零。其他尚无官方位定义的字段仅写进 reason_codes 和摘要，不擅自否决观测。

- [ ] **Step 4: 实现并标注 SSH 公式版本**

~~~python
range_corrected = (
    fields["model_dry_tropo_corr"] + fields["rad_wet_tropo_corr"]
    + fields["iono_corr_alt_ku"] + fields["sea_state_bias_ku"]
    + fields["inv_bar_corr"] + fields["hf_fluctuations_corr"]
)
tide = fields["ocean_tide_sol1"] + fields["solid_earth_tide"] + fields["pole_tide"] + fields["load_tide_sol1"]
ssh = fields["alt"] - (fields["range_ku"] + range_corrected) - tide
~~~

任一核心项缺失时对应 SSH 必须为 NaN，摘要写入 sshFormulaVersion=hy2d-idr-2mc-baseline-v1、变量列表和各分项缺失率。

- [ ] **Step 5: 运行测试并提交**

Run: pytest tests/test_track_quality.py tests/test_ssh.py -v

Expected: PASS。

~~~bash
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: add HY-2D baseline quality and SSH"
~~~

### Task 6: 实现沿轨特征与异常检测

**Files:**
- Create: rs-algorithm-worker/src/rs_algorithm_worker/algorithms/track_features.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/algorithms/track_anomaly.py
- Create: rs-algorithm-worker/tests/test_track_features.py
- Create: rs-algorithm-worker/tests/test_track_anomaly.py
- Modify: rs-algorithm-worker/src/rs_algorithm_worker/capabilities.py

- [ ] **Step 1: 写出窗口统计、MAD 和跳变检测的失败测试**

~~~python
def test_features_report_window_mean_std_gradient_and_valid_ratio():
    result = extract_track_features(track, window_size=3)
    assert result.windows[0]["validRatio"] == pytest.approx(1.0)
    assert result.windows[0]["sshMean"] == pytest.approx(2.0)

def test_anomaly_detects_mad_outlier_and_adjacent_jump():
    result = detect_track_anomalies(np.array([1.0, 1.1, 8.0, 1.2]))
    assert {"MAD_OUTLIER", "TRACK_JUMP"}.issubset(set(result.reason_codes[2]))
~~~

- [ ] **Step 2: 运行失败测试**

Run: pytest tests/test_track_features.py tests/test_track_anomaly.py -v

Expected: FAIL，提示模块不存在。

- [ ] **Step 3: 实现仅针对有效 SSH 的算法**

窗口按连续有效样本数划分，不假设固定空间分辨率。每窗输出 startIndex、endIndex、sshMean、sshStd、sshTrend、sshGradientMean、validRatio。异常分数用滑动中位数和 1.4826 * MAD 标准化；MAD 为零时只执行物理阈值和相邻差分判断。

- [ ] **Step 4: 注册沿轨能力，保持融合和图像显著性不支持**

~~~python
CapabilitySpec("features", "track-statistics-v1", frozenset({"ALONG_TRACK_OBSERVATION"}), ()),
CapabilitySpec("anomaly", "track-mad-v1", frozenset({"ALONG_TRACK_OBSERVATION"}), ()),
~~~

- [ ] **Step 5: 运行测试并提交**

Run: pytest tests/test_track_features.py tests/test_track_anomaly.py tests/test_operator_contracts.py -v

Expected: PASS。

~~~bash
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: add along-track features and anomaly detection"
~~~

### Task 7: 实现标准输出和统计网格

**Files:**
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/track_output.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/operators/track_grid.py
- Create: rs-algorithm-worker/tests/test_track_grid.py
- Modify: rs-algorithm-worker/tests/test_hy2d_pipeline.py

- [ ] **Step 1: 写出标准输出与无插值网格的失败测试**

~~~python
def test_standard_outputs_are_written_with_expected_columns(tmp_path):
    files = write_standard_track_outputs(track, features, anomalies, tmp_path)
    assert set(files) == {"track.nc", "track.parquet", "track.csv", "track.geojson", "summary.json"}

def test_grid_aggregates_valid_ssh_without_interpolation():
    grid = aggregate_track_grid(track, spacing_degrees=1.0)
    assert grid.count[0, 0] == 2
    assert grid.ssh_mean[0, 0] == pytest.approx(1.5)
~~~

- [ ] **Step 2: 运行失败测试**

Run: pytest tests/test_hy2d_pipeline.py tests/test_track_grid.py -v

Expected: FAIL，提示输出或网格模块不存在。

- [ ] **Step 3: 实现五种标准输出**

CSV 和 Parquet 必有 time、longitude、latitude、quality_status、quality_reason_codes、ssh；GeoJSON 使用 LineString 表示轨迹并输出独立异常点 FeatureCollection；NetCDF 写入处理版本、质量策略版本和 SSH 公式版本；JSON 摘要包含通过率、异常数、字段缺失率和科学边界提示。

- [ ] **Step 4: 实现仅统计的规则格网**

每格仅写 ssh_mean、ssh_std、count、valid_ratio，并在输出元数据写入 method=bin-statistics-no-interpolation。COG 的 band 顺序与这些变量顺序一致。

- [ ] **Step 5: 运行测试并提交**

Run: pytest tests/test_hy2d_pipeline.py tests/test_track_grid.py -v

Expected: PASS。

~~~bash
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: write HY-2D track products and statistic grids"
~~~

### Task 8: 生成 HY-2D plan-v2 并执行 Worker 阶段

**Files:**
- Modify: rs-algorithm-worker/src/rs_algorithm_worker/product_adapters/hy2d_altimeter.py
- Create: rs-algorithm-worker/src/rs_algorithm_worker/hy2d_pipeline.py
- Modify: rs-algorithm-worker/src/rs_algorithm_worker/registry.py
- Modify: rs-algorithm-worker/tests/test_hy2d_altimeter.py
- Modify: rs-algorithm-worker/tests/test_cli.py

- [ ] **Step 1: 写出 HY-2D 专属 plan-v2 的失败测试**

~~~python
def test_hy2d_plan_declares_track_artifacts_and_optional_grid(hy2d_manifest):
    candidate = inspect_manifest(hy2d_manifest)[0]
    plan = build_hy2d_plan(candidate, {"grid": {"enabled": True, "spacingDegrees": 0.25}})
    assert plan["version"] == "plan-v2"
    assert "GRID_TRACK_OPTIONAL" in {stage["stageKey"] for stage in plan["stages"]}
~~~

- [ ] **Step 2: 运行测试，确认现有适配器仍返回通用 plan-v1**

Run: pytest tests/test_hy2d_altimeter.py::test_hy2d_plan_declares_track_artifacts_and_optional_grid -v

Expected: FAIL。

- [ ] **Step 3: 实现产品检查、参数校验和 DAG**

适配器确认 .nc 与 .meta.xml 成对存在，将 profile 固定为 HY-2D-ALTIMETER/v1、shape 固定为 ALONG_TRACK_OBSERVATION。只接受如下参数并拒绝未知键：

~~~json
{
  "qualityPolicyVersion": "hy2d-idr-2mc-baseline-v1",
  "featureWindowSize": 21,
  "anomaly": {"madThreshold": 5.0, "jumpThresholdMeters": 0.5},
  "grid": {"enabled": false, "spacingDegrees": 0.25}
}
~~~

窗口必须是正奇数，阈值必须为正数，格网间隔必须在 (0, 5]。

- [ ] **Step 4: 实现受契约约束的 Worker dispatcher**

~~~python
if request["operation"] == "execute-stage" and request["profileCode"] == "HY-2D-ALTIMETER":
    return execute_hy2d_stage(payload, manifest)
~~~

每阶段只读 manifest 文件，只写 manifest.output.path。返回前逐一核对计划 StageOutput，计算文件大小和 SHA-256，使用 rs://output/.../<relative-path> 返回 URI。

- [ ] **Step 5: 运行合同、CLI、适配器测试并提交**

Run: pytest tests/test_hy2d_altimeter.py tests/test_hy2d_pipeline.py tests/test_cli.py tests/test_contracts.py -v

Expected: PASS。

~~~bash
git add rs-algorithm-worker/src rs-algorithm-worker/tests
git commit -m "feat: execute HY-2D along-track processing DAG"
~~~

### Task 9: 使用真实样例端到端验收并同步限制说明

**Files:**
- Modify: rs-algorithm-worker/tests/conftest.py
- Modify: rs-algorithm-worker/tests/test_hy2d_altimeter.py
- Create: docs/hy2d-altimeter-baseline-product.md

- [ ] **Step 1: 配置只读真实样例路径**

~~~powershell
$env:MULTISOURCE_HY2D_SAMPLE_ROOT = 'D:\Lwy_project_space\RSImageProcess\otherfile\H2D_OPER_IDR_2MC_0188_0242_20260701'
~~~

所有解压文件和结果均写 pytest tmp_path；禁止修改 otherfile 下的原始数据。

- [ ] **Step 2: 写出真实样例完整产物测试**

~~~python
@pytest.mark.real_sample
def test_real_hy2d_package_produces_baseline_track_outputs(hy2d_manifest, tmp_path):
    result = execute_full_hy2d_pipeline(hy2d_manifest, tmp_path)
    assert result.summary["qualityPolicyVersion"] == "hy2d-idr-2mc-baseline-v1"
    assert result.summary["sshFormulaVersion"] == "hy2d-idr-2mc-baseline-v1"
    assert result.output_files["track.parquet"].is_file()
~~~

- [ ] **Step 3: 运行全量 Worker 和 Java 测试**

Run: pytest -q

Expected: PASS 或明确的样例 SKIPPED，无 FAILED。

Run: mvn -pl htht-modules/htht-multisource,htht-admin test

Expected: PASS。

- [ ] **Step 4: 编写产品限制文档并提交**

文档必须说明输入产品、字段解码规则、质量策略版本、SSH 公式版本、输出字段、网格不插值限制，以及取得官方规范后必须复核质量位、改正项符号和阈值。

~~~bash
git add docs rs-algorithm-worker/tests
git commit -m "docs: document HY-2D baseline product limits"
~~~

## 执行前检查清单

- [ ] artifact-root 位于不受 Worker 临时目录清理影响的位置。
- [ ] Worker 环境已有 netCDF4 并可读取 NETCDF3_CLASSIC。
- [ ] 原始样例目录只读，测试临时目录不在 otherfile。
- [ ] 前端在 Java 产物提升完成后才显示下载入口；地图用语必须是“沿轨异常”和“沿轨统计网格”。

## 自检

- 覆盖解析、质量过滤、SSH、特征、异常、标准输出、可选网格、阶段产物交接与端到端验收。
- 未包含影像多波段融合、图像显著性、复杂空间插值或多星科学融合。
- 所有尚未由官方 IDR/IGDR 细则确认的规则均显式使用基线版本名，后续可替换。
