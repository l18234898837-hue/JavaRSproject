# 多源卫星数据处理系统后端部署手册

## 1. 文档说明

本文档用于部署多源卫星数据处理系统后端，包括：

- Java 后端服务；
- `htht-multisource` 多源处理模块；
- `rs-algorithm-worker` Python Worker；
- PostgreSQL/PostGIS 数据库；
- 多源处理相关数据库迁移；
- 后端执行接口验证。

本文档只覆盖后端部署，不包含：

- 前端代码部署；
- 前后端联调；
- 前端页面配置；
- 生产环境反向代理配置；
- 独立的分布式任务队列部署。

当前实现分支：

```text
feat/multisource-backend-foundation
当前已完成的后端提交包括：
13b12b1 chore: align multisource backend configuration
011ad92 feat: complete multisource execution orchestration
部署前应先获取远端最新代码：
git fetch origin
git checkout feat/multisource-backend-foundation
git pull --ff-only origin feat/multisource-backend-foundation
如果部署环境使用其他正式发布分支，应以项目负责人指定的分支或标签为准。
2. 系统组成
后端处理链路如下：
客户端
  |
  | 已认证 HTTP 请求
  v
Java 后端
  |
  | 创建批次、候选快照、处理任务、计划快照
  v
PostgreSQL/PostGIS
  |
  | 服务端生成执行上下文和授权信息
  v
Java Worker Gateway
  |
  | 调用本地 Python Worker
  v
rs-algorithm-worker
  |
  | 检查、规划、执行处理阶段
  v
处理输出目录
Java 后端负责：
- 用户认证和访问控制；
- 批次和任务管理；
- 多源候选数据快照；
- 处理计划快照；
- 阶段状态管理；
- 阶段调度；
- 取消、重试和超时恢复；
- Worker 调用；
- 输出物校验和登记；
- 任务进度查询；
- 执行事件记录。
Python Worker 负责：
- 数据检查；
- 产品候选识别；
- 处理计划生成；
- 单个处理阶段执行；
- 返回结构化处理结果。
数据库负责：
- 业务任务和批次持久化；
- 候选产品快照持久化；
- 任务输入资产持久化；
- 任务阶段持久化；
- 执行事件持久化；
- 产物登记；
- 处理状态查询。
3. 部署前提
部署人员需要具备：
- Git 仓库访问权限；
- PostgreSQL 数据库创建权限；
- PostgreSQL schema、表和扩展创建权限；
- Java 服务运行权限；
- Python Worker 运行权限；
- 输入数据目录读取权限；
- Worker 工作目录读写权限；
- 输出目录读写权限。
部署时不要使用以下本地开发目录：
.m2/
.m2-local/
.tools/
rs-algorithm-worker/.pytest-tmp/
这些目录属于开发机缓存或测试临时目录，不是部署源代码。部署服务器应自行安装或准备对应工具和依赖。
4. 软件环境
4.1 Java
要求：
JDK 17
检查：
java -version
javac -version
应确认 Java 主版本为 17。
Linux 示例：
export JAVA_HOME=/path/to/jdk-17
export PATH="$JAVA_HOME/bin:$PATH"
Windows PowerShell 示例：
$env:JAVA_HOME = 'C:\Program Files\Eclipse Adoptium\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
4.2 Maven
检查：
mvn -version
应确认 Maven 使用的是 JDK 17。
Windows 环境如果项目提供了 Maven 工具目录，可以使用：
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -version
.tools 如果没有被提交到远端，部署人员需要自行安装 Maven，或者由部署环境统一提供。
4.3 PostgreSQL
要求：
PostgreSQL 17
检查：
psql --version
4.4 PostGIS
多源模块使用 PostgreSQL 数据库能力。部署前应确认目标数据库已经安装 PostGIS。
检查：
SELECT PostGIS_Full_Version();
如果尚未安装 PostGIS，需要由数据库管理员根据操作系统安装对应扩展包。
4.5 Python
Worker 需要 Python 运行环境。
检查：
python --version
或：
python3 --version
具体 Python 版本应以 rs-algorithm-worker 目录中的项目依赖文件和实际算法依赖要求为准。
4.6 Worker 相关系统依赖
根据实际 Worker 算法和输入数据类型，部署环境可能需要：
- GDAL；
- HDF/HDF5 支持；
- NetCDF 支持；
- rasterio；
- numpy；
- xarray；
- pyproj；
- shapely；
- 其他算法依赖。
不能仅根据 Java 后端构建成功判断 Worker 环境已经完整。Worker 必须单独验证。
5. 获取代码
建议在部署服务器创建独立目录：
mkdir -p /opt/JavaRSproject
cd /opt/JavaRSproject
克隆仓库：
git clone git@github.com:l18234898837-hue/JavaRSproject.git
cd JavaRSproject
获取目标分支：
git fetch origin
git checkout -B feat/multisource-backend-foundation origin/feat/multisource-backend-foundation
确认当前提交：
git log -1 --oneline
当前功能分支最近提交应包含：
13b12b1 chore: align multisource backend configuration
检查工作区：
git status --short
部署环境不应存在未预期的源代码修改。
6. 数据库准备
6.1 数据库规划
开发环境配置中使用的数据库名称为：
satellite_data_server_dev
默认开发连接示例：
jdbc:postgresql://127.0.0.1:5433/satellite_data_server_dev
生产或其他部署环境不要直接照搬此地址，应根据实际部署拓扑配置。
建议为应用创建独立数据库用户，例如：
htht_rs_app
不要直接使用 PostgreSQL 超级用户运行应用。
6.2 创建数据库
以下命令仅为示例，实际数据库管理员用户名、端口和认证方式应根据部署环境调整。
createdb -h 127.0.0.1 -p 5433 -U postgres satellite_data_server_dev
或者使用 SQL：
CREATE DATABASE satellite_data_server_dev;
6.3 创建应用用户
示例：
CREATE USER htht_rs_app WITH PASSWORD '<请替换为安全密码>';
GRANT CONNECT ON DATABASE satellite_data_server_dev TO htht_rs_app;
切换到目标数据库后，根据迁移脚本实际创建的 schema 和对象授予权限。
不要把真实密码写入 Git 仓库。
6.4 启用 PostGIS
连接目标数据库：
psql -h 127.0.0.1 -p 5433 -U postgres -d satellite_data_server_dev
执行：
CREATE EXTENSION IF NOT EXISTS postgis;
确认：
SELECT PostGIS_Full_Version();
6.5 数据库源表保护要求
以下两张表属于既有业务源表：
st_island_info
sd_observe_data
本次多源后端实现不修改这两张表的结构，也不应在部署迁移中删除、重建或清空这两张表。
部署前应确认：
- 目标数据库已经导入正确的业务库；
- 既有源表数据已经备份；
- 多源迁移脚本只处理多源模块所需的表；
- 不执行无关的全库清理脚本；
- 不执行 DROP DATABASE；
- 不执行面向业务源表的 TRUNCATE。
7. 数据库迁移
7.1 迁移脚本位置
多源模块迁移脚本位于：
script/sql/migrations/
当前多源迁移文件包括：
V20260819_01__create_multisource_core_tables.sql
V20260819_02__create_multisource_indexes.sql
V20260819_03__seed_multisource_profiles.sql
V20260820_01__add_product_metadata_snapshots.sql
V20260820_02__seed_gf6_browse_profile.sql
V20260821_01__add_execution_event_id.sql
实际部署前应以目标分支中该目录的最新文件为准。
7.2 迁移前备份
执行正式迁移前，先备份目标数据库。
示例：
pg_dump \
  -h 127.0.0.1 \
  -p 5433 \
  -U postgres \
  -Fc \
  -f satellite_data_server_dev_before_multisource.dump \
  satellite_data_server_dev
如果数据库较大，备份时间可能较长。必须确认备份文件可读，并且恢复方案已经准备好。
7.3 迁移执行原则
迁移应满足：
- 按文件名版本顺序执行；
- 迁移执行前确认数据库连接目标正确；
- 不连接生产库做首次验证；
- 首次验证优先使用独立测试数据库或独立 schema；
- 迁移过程中保留完整日志；
- 迁移失败时停止后续脚本；
- 不绕过失败继续执行；
- 迁移完成后进行表、索引和种子数据检查。
迁移脚本不应直接指向开发数据库以外的未知数据库。
7.4 使用独立 schema 验证迁移
项目中的 PostgreSQL 集成测试使用以下环境变量控制：
MULTISOURCE_IT_JDBC_URL
MULTISOURCE_IT_USERNAME
MULTISOURCE_IT_PASSWORD
只有三个变量都配置时，迁移集成测试才会连接 PostgreSQL。
测试逻辑会：
1. 创建随机 schema；
2. 在随机 schema 中执行迁移；
3. 验证表、索引和种子数据；
4. 验证仓储读写和回滚；
5. 删除随机 schema；
6. 确认随机 schema 已不存在。
示例：
Linux/macOS：
export MULTISOURCE_IT_JDBC_URL='jdbc:postgresql://127.0.0.1:5433/satellite_data_server_it'
export MULTISOURCE_IT_USERNAME='htht_rs_app'
export MULTISOURCE_IT_PASSWORD='<测试密码>'
Windows PowerShell：
$env:MULTISOURCE_IT_JDBC_URL = 'jdbc:postgresql://127.0.0.1:5433/satellite_data_server_it'
$env:MULTISOURCE_IT_USERNAME = 'htht_rs_app'
$env:MULTISOURCE_IT_PASSWORD = '<测试密码>'
运行：
mvn -pl htht-modules/htht-multisource -am \
  -Dtest=PostgreSqlMigrationIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
这一步不要把 MULTISOURCE_IT_JDBC_URL 指向包含正式业务数据的数据库。
7.5 正式目标库迁移
正式目标库的迁移方式应由项目现有数据库发布流程决定。常见方式包括：
- Flyway；
- Liquibase；
- DBA 审核后执行 SQL；
- 受控发布脚本；
- 数据库版本管理平台。
如果项目尚未提供统一迁移工具，应由数据库管理员按以下方式执行：
1. 备份数据库；
2. 确认当前数据库版本；
3. 检查目标迁移文件；
4. 按版本顺序执行；
5. 每个文件执行后检查结果；
6. 记录迁移日志；
7. 验证多源模块表和索引；
8. 验证既有源表没有变化。
不要将“应用启动成功”作为迁移成功的唯一判断依据。
7.6 迁移后检查
迁移后至少检查：
SELECT table_name
FROM information_schema.tables
WHERE table_name IN (
    'rs_processing_batch',
    'rs_batch_product',
    'rs_processing_task',
    'rs_task_product',
    'rs_task_input_asset',
    'rs_task_stage',
    'rs_task_event',
    'rs_artifact',
    'rs_product_profile',
    'rs_profile_capability'
)
ORDER BY table_name;
检查既有源表仍然存在：
SELECT table_name
FROM information_schema.tables
WHERE table_name IN (
    'st_island_info',
    'sd_observe_data'
)
ORDER BY table_name;
检查执行事件扩展字段：
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'rs_task_event'
  AND column_name = 'execution_id';
检查候选快照表：
SELECT table_name
FROM information_schema.tables
WHERE table_name = 'rs_batch_product';
检查 PostGIS：
SELECT PostGIS_Full_Version();
8. Java 后端配置
8.1 配置文件
开发环境配置位于：
htht-admin/src/main/resources/application-dev.yml
当前开发数据库配置示例：
spring:
  datasource:
    type: com.zaxxer.hikari.HikariDataSource
    driverClassName: org.postgresql.Driver
    url: jdbc:postgresql://127.0.0.1:5433/satellite_data_server_dev
    username: htht_rs_dev
    password: <请使用部署环境密码>
生产环境不应直接修改 Git 中的公共配置来写入真实密码。建议使用：
- 环境变量；
- 外部配置文件；
- 配置中心；
- Secret 管理系统；
- 容器 Secret；
- 操作系统服务账户环境配置。
8.2 Worker 配置
多源 Worker 命令必须通过部署配置提供：
MULTISOURCE_WORKER_COMMAND
该配置不能由客户端提交，也不能通过接口让客户端指定。
示例：
Linux/macOS：
export MULTISOURCE_WORKER_COMMAND='/opt/JavaRSproject/rs-algorithm-worker/.venv/bin/python -m <worker_module>'
Windows PowerShell 示例：
$env:MULTISOURCE_WORKER_COMMAND = 'C:\opt\JavaRSproject\rs-algorithm-worker\.venv\Scripts\python.exe -m <worker_module>'
实际 Worker 启动模块必须以 rs-algorithm-worker 当前代码提供的入口为准。部署人员应先查看：
rs-algorithm-worker/README.md
rs-algorithm-worker/pyproject.toml
rs-algorithm-worker/requirements.txt
rs-algorithm-worker/setup.cfg
如果目录中没有 README，应检查 Python 包入口、脚本文件和测试文件，确认实际启动方式。
8.3 Worker 工作目录
Java 后端需要配置 Worker 工作根目录。该目录应满足：
- Java 服务账户可写；
- Worker 进程可读写；
- 不位于 Git 仓库源代码目录中；
- 不允许普通用户随意访问；
- 有足够磁盘空间；
- 具有定期清理策略；
- 不与输入数据目录混用；
- 不与正式输出归档目录混用。
示例：
/var/lib/htht-rs/multisource/worker-work
Windows 示例：
D:\htht-rs\multisource\worker-work
执行阶段使用服务端生成的执行目录，不能使用客户端提交的物理路径。
8.4 输入数据目录
输入数据目录应：
- 对 Java 后端和 Worker 服务账户可读；
- 不允许 Worker 越权读取任意系统路径；
- 使用稳定的逻辑 URI 映射；
- 不允许通过 HTTP 请求直接传入物理路径；
- 需要做好权限隔离和数据生命周期管理。
8.5 输出目录
输出目录应：
- 对 Worker 可写；
- 对 Java 后端可读；
- 具备容量监控；
- 具备清理或归档策略；
- 不允许客户端指定任意物理路径；
- 不允许输出文件逃逸到受控工作根目录之外。
系统会对 Worker 输出进行校验，包括：
- 路径归属；
- 文件是否存在；
- 是否为普通文件；
- 是否可读；
- 文件大小；
- SHA-256 校验值；
- 逻辑输出 URI；
- 任务和阶段归属。
8.6 多源统一存储根目录
多源运行数据必须放在代码目录之外。部署时只配置一个存储根目录：

```bash
MULTISOURCE_STORAGE_ROOT=/srv/rsimageprocess-data
```

应用会自动使用以下子目录：

```text
/srv/rsimageprocess-data/source/files       原始上传文件
/srv/rsimageprocess-data/source/incoming    ZIP 分片和解压临时目录
/srv/rsimageprocess-data/tasks/work         Worker 执行临时目录
/srv/rsimageprocess-data/tasks/artifacts    任务产物目录
/srv/rsimageprocess-data/system-tmp         Java 临时目录
```

Ubuntu 部署前创建目录并授权给服务用户：

```bash
sudo install -d -o htht-rs -g htht-rs \
  /srv/rsimageprocess-data/source/files \
  /srv/rsimageprocess-data/source/incoming \
  /srv/rsimageprocess-data/tasks/work \
  /srv/rsimageprocess-data/tasks/artifacts \
  /srv/rsimageprocess-data/system-tmp
```

不需要再分别配置 `FILE_UPLOAD_PATH`、`MULTISOURCE_WORK_ROOT` 或
`MULTISOURCE_ARTIFACT_ROOT`；只有在需要覆盖默认布局时才单独设置这些变量。
存储根目录应使用独立磁盘或挂载卷，不能放在 `/opt/JavaRSproject` 代码目录中。

9. Python Worker 安装
进入 Worker 目录：
cd rs-algorithm-worker
创建虚拟环境：
Linux/macOS：
python3 -m venv .venv
source .venv/bin/activate
Windows PowerShell：
py -3 -m venv .venv
.\.venv\Scripts\Activate.ps1
升级基础工具：
python -m pip install --upgrade pip setuptools wheel
安装项目依赖。
如果项目提供 requirements.txt：
python -m pip install -r requirements.txt
如果项目提供 requirements-dev.txt：
python -m pip install -r requirements-dev.txt
如果项目使用 pyproject.toml：
python -m pip install -e .
如果需要开发测试依赖：
python -m pip install -e ".[dev]"
实际命令应以 Worker 项目当前依赖文件为准。
9.1 Worker 测试
从 rs-algorithm-worker 目录执行：
python -m pytest -q --basetemp .pytest-tmp
当前代码回归结果记录：
14 passed, 6 skipped
跳过项是没有配置真实样例数据环境的可选测试，不代表 Worker 核心测试失败。
部署环境如果配置了真实样例数据，应额外执行真实样例测试，并记录：
- 输入数据位置；
- 产品类型；
- 使用的规则包版本；
- 输出文件；
- 校验结果；
- 运行时间；
- 失败日志。
10. Java 后端构建
回到项目根目录：
cd ..
确认 Java 和 Maven：
java -version
mvn -version
运行多源和 Admin 相关 Java 回归：
mvn -pl htht-modules/htht-multisource,htht-admin -am test
当前验证结果：
htht-multisource:
107 tests, 0 failures/errors, 3 environment-gated skips

htht-admin:
17 tests, 0 failures/errors/skips
构建后端：
mvn -pl htht-admin -am package -DskipTests
如果部署流程要求完整测试后再打包，执行：
mvn -pl htht-admin -am package
如果项目存在私有 Maven 依赖，部署环境需要：
- 配置公司 Maven 仓库；
- 配置凭据；
- 确认网络访问；
- 配置正确的本地仓库；
- 不把本地 .m2 缓存提交到 Git。
10.1 构建产物
构建完成后检查：
htht-admin/target/
实际 JAR 文件名以 Maven 构建输出为准。
检查：
ls -la htht-admin/target
Windows PowerShell：
Get-ChildItem htht-admin\target
11. Java 后端启动
11.1 开发环境启动
如果项目现有启动方式使用 Spring profile，可以使用：
Linux/macOS：
export SPRING_PROFILES_ACTIVE=dev
java -jar htht-admin/target/<实际生成的jar文件>.jar
Windows PowerShell：
$env:SPRING_PROFILES_ACTIVE = 'dev'
java -jar htht-admin\target\<实际生成的jar文件>.jar
如果项目现有启动类或启动脚本有明确要求，以项目实际启动方式为准。
11.2 生产环境启动
生产环境建议：
- 使用独立操作系统用户；
- 使用 systemd、Windows Service、容器或其他服务管理器；
- 不使用开发 profile；
- 使用外部配置；
- 配置日志目录；
- 配置 JVM 内存；
- 配置健康检查；
- 配置服务自动重启；
- 配置优雅关闭；
- 配置 Worker 子进程清理；
- 配置文件权限。
Linux systemd 示例：
[Unit]
Description=HTHT Remote Sensing Backend
After=network.target postgresql.service

[Service]
User=htht-rs
WorkingDirectory=/opt/JavaRSproject
Environment="SPRING_PROFILES_ACTIVE=prod"
EnvironmentFile=/etc/htht-rs/multisource.env
ExecStart=/usr/bin/java -jar /opt/JavaRSproject/htht-admin/target/<实际jar文件>.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
保存后：
sudo systemctl daemon-reload
sudo systemctl enable htht-rs
sudo systemctl start htht-rs
sudo systemctl status htht-rs
以上 systemd 内容是部署示例，需要根据项目实际 JAR 文件名、配置方式和目录调整。
12. 认证要求
多源处理执行接口默认要求登录。
系统功能不应在未认证状态下直接调用。部署人员需要确认：
- 用户认证模块已经启用；
- 数据库中存在可用用户；
- 用户具备访问多源模块的权限；
- Sa-Token 或现有认证机制配置正确；
- 登录后能够获取有效会话；
- 跨域和 Cookie 策略符合实际部署拓扑；
- 反向代理不会丢失认证头或 Cookie。
执行接口包括：
POST /api/multisource/processing-tasks/{taskId}/dispatch
POST /api/multisource/processing-tasks/{taskId}/cancel
POST /api/multisource/processing-stages/{stageId}/retry
GET  /api/multisource/processing-tasks/{taskId}/progress
接口只接受路径中的任务 ID 或阶段 ID，不接受客户端提交的：
- 物理文件路径；
- Worker 命令；
- Worker 执行参数；
- 输出物理路径；
- 执行 ID；
- 计划快照；
- 阶段依赖关系；
- 服务端授权信息。
13. 后端接口验证
以下验证需要替换：
<backend-host>
<session-cookie>
<task-id>
<stage-id>
13.1 未登录访问
curl -i \
  "http://<backend-host>/api/multisource/processing-tasks/101/progress"
预期：
- 返回未认证或登录要求；
- 不应直接返回其他用户的任务数据；
- 不应执行处理任务。
13.2 查询任务进度
curl -i \
  -H "Cookie: <session-cookie>" \
  "http://<backend-host>/api/multisource/processing-tasks/<task-id>/progress"
预期：
- 返回当前任务进度；
- 只返回当前登录用户有权访问的任务；
- 不暴露物理路径、Worker 命令、授权 grant 或内部执行 ID。
13.3 请求调度
curl -i -X POST \
  -H "Cookie: <session-cookie>" \
  "http://<backend-host>/api/multisource/processing-tasks/<task-id>/dispatch"
该请求不需要请求体。
预期：
- 服务器验证任务归属；
- 服务器根据任务计划快照选择可执行阶段；
- 服务器生成执行 ID和执行授权；
- 服务器调用 Worker；
- 任务和阶段状态按处理结果更新。
客户端不能通过请求体提交以下内容：
physicalPath
workerCommand
outputUri
executionId
planSnapshot
stageType
13.4 取消任务
curl -i -X POST \
  -H "Cookie: <session-cookie>" \
  "http://<backend-host>/api/multisource/processing-tasks/<task-id>/cancel"
预期：
- 任务归属验证通过后执行取消；
- 可运行阶段被跳过；
- 正在运行的阶段记录用户取消状态；
- 数据库事务提交后再通知 Worker 取消；
- 已完成阶段保留历史记录。
13.5 重试阶段
curl -i -X POST \
  -H "Cookie: <session-cookie>" \
  "http://<backend-host>/api/multisource/processing-stages/<stage-id>/retry"
预期：
- 校验当前用户是否拥有对应任务；
- 只允许失败或恢复中的阶段重试；
- 单个阶段最多三次总尝试；
- 已取消、已成功或不可重试的任务拒绝重试。
14. 数据处理流程验证
建议使用一组非生产样例数据进行完整验证。
验证流程：
1. 登录系统；
2. 创建处理批次；
3. 执行产品检查；
4. 确认候选产品被保存为批次快照；
5. 创建处理任务；
6. 确认任务保存了候选快照；
7. 确认处理计划保存为任务快照；
8. 调度第一个可执行阶段；
9. 确认 Worker 被调用；
10. 确认阶段状态发生变化；
11. 确认执行事件写入数据库；
12. 确认输出物通过校验后登记；
13. 确认依赖阶段自动具备执行条件；
14. 查询任务进度；
15. 测试取消任务；
16. 测试失败阶段重试；
17. 测试超时恢复；
18. 检查日志和数据库状态。
检查任务阶段：
SELECT
    id,
    task_id,
    stage_key,
    status,
    attempt,
    execution_id,
    started_at,
    finished_at
FROM rs_task_stage
WHERE task_id = <task_id>
ORDER BY id;
检查执行事件：
SELECT
    id,
    execution_id,
    task_id,
    stage_id,
    attempt,
    event_type,
    event_sequence,
    received_at,
    applied
FROM rs_task_event
WHERE task_id = <task_id>
ORDER BY id;
检查产物：
SELECT
    id,
    task_id,
    stage_id,
    artifact_type,
    uri,
    checksum,
    size_bytes
FROM rs_artifact
WHERE task_id = <task_id>
ORDER BY id;
15. 五类产品支持说明
当前系统已经登记五类支持产品能力，其中包括 GF-6 浏览类数据能力。
数据库种子数据和产品能力配置应以迁移脚本中的最新内容为准。
部署后应检查产品支持配置：
SELECT
    product_code,
    profile_version,
    data_shape,
    support_status
FROM rs_product_profile
ORDER BY product_code;
检查能力配置：
SELECT
    product_code,
    capability_code
FROM rs_profile_capability
ORDER BY product_code, capability_code;
GF-6 浏览类数据应根据系统登记的能力执行对应的浏览特征处理。是否能够执行真实样例处理，还取决于：
- Worker 是否安装对应算法；
- 输入数据格式是否符合当前规则；
- 规则包版本是否匹配；
- 样例数据是否完整；
- 运行环境是否具备必要的 GDAL/HDF 等依赖。
数据库中存在产品能力记录，不等于部署环境已经具备全部算法运行依赖。
16. 安全要求
16.1 密码管理
不要将以下内容提交到 Git：
- 数据库真实密码；
- Worker 凭据；
- Token；
- 私钥；
- 云存储密钥；
- 生产配置；
- 生产数据路径。
使用：
- 环境变量；
- Secret 文件；
- 配置中心；
- 密钥管理系统。
16.2 文件路径安全
客户端不得提交物理文件路径。
系统应使用：
- 服务端维护的逻辑 URI；
- 服务端维护的输入资产映射；
- 服务端生成的输出 URI；
- 受控 Worker 工作目录；
- 路径规范化和目录包含检查。
16.3 Worker 安全
Worker 命令必须由部署环境配置，不能由客户端指定。
Worker 运行账户应：
- 不使用 root；
- 只拥有必要目录权限；
- 不能写入 Java 源代码目录；
- 不能任意读取系统敏感文件；
- 不能任意访问数据库管理端口；
- 不能访问不必要的网络资源。
16.4 数据库安全
应用账户不应拥有：
- 数据库超级用户权限；
- 任意数据库创建权限；
- 任意 schema 删除权限；
- 不必要的扩展安装权限。
迁移账户和运行账户可以分离。
17. 日志和监控
部署后应监控：
- Java 服务启动状态；
- 数据库连接状态；
- Worker 启动失败；
- Worker 非零退出；
- Worker 协议错误；
- 阶段超时；
- 输出文件校验失败；
- 数据库事务失败；
- 任务取消；
- 阶段重试；
- 任务恢复；
- 磁盘空间；
- Worker 工作目录大小；
- 输出目录大小。
每个处理任务至少应能够通过以下信息关联日志：
taskId
stageId
executionId
attempt
workerId
requestId
其中内部执行 ID 不应作为公开 API 响应字段暴露给客户端，但可以写入服务端日志和数据库事件记录，用于排障。
18. 故障排查
18.1 Java 服务无法启动
检查：
java -version
mvn -version
检查：
- JDK 是否为 17；
- Maven 依赖是否能够下载；
- PostgreSQL 地址是否正确；
- 用户名和密码是否正确；
- 数据库是否可连接；
- PostGIS 是否安装；
- Worker 必需配置是否存在；
- 端口是否被占用；
- 配置文件 profile 是否正确。
18.2 数据库连接失败
检查：
psql -h <host> -p <port> -U <username> -d <database>
确认：
- PostgreSQL 服务已启动；
- 端口正确；
- 数据库名称正确；
- 用户密码正确；
- pg_hba.conf 允许连接；
- 防火墙允许访问；
- 数据库用户有 CONNECT 权限；
- 应用连接池配置正确。
18.3 迁移失败
处理原则：
1. 停止执行后续迁移；
2. 保存完整错误日志；
3. 记录已经成功执行的脚本；
4. 检查目标数据库是否为正确数据库；
5. 检查 PostgreSQL/PostGIS 版本；
6. 检查脚本执行权限；
7. 使用备份或回滚方案恢复；
8. 不直接手动删除未知表；
9. 不清空 st_island_info 或 sd_observe_data。
18.4 Worker 命令未配置
典型现象：
multisource.worker.command must be configured through MULTISOURCE_WORKER_COMMAND
检查：
echo "$MULTISOURCE_WORKER_COMMAND"
Windows PowerShell：
$env:MULTISOURCE_WORKER_COMMAND
确认：
- 环境变量已注入 Java 服务进程；
- Python 可执行文件路径正确；
- Worker 入口模块正确；
- Java 服务账户可以执行该命令；
- 工作目录权限正确。
18.5 Worker 启动后立即失败
检查：
python --version
python -m pytest -q --basetemp .pytest-tmp
检查：
- 虚拟环境是否启用；
- 依赖是否安装；
- GDAL/HDF 等系统库是否存在；
- 输入文件格式是否正确；
- 输出目录是否可写；
- Worker 协议版本是否匹配；
- Worker 日志中的异常。
18.6 任务无法调度
检查：
- 当前用户是否登录；
- 当前用户是否拥有任务；
- 任务是否处于可调度状态；
- 是否存在满足依赖的 PENDING 阶段；
- 前置阶段是否成功；
- 任务是否已经取消；
- 是否已达到重试上限；
- 是否存在未恢复的超时阶段。
18.7 输出物校验失败
检查：
- 输出文件是否存在；
- 是否为普通文件；
- 是否位于受控 Worker 输出目录；
- 文件是否可读；
- 文件大小是否正确；
- SHA-256 是否正确；
- 输出 URI 是否属于任务服务端输出 URI；
- Worker 是否提前清理了输出；
- Worker 是否写入了错误的文件类型。
不要通过关闭校验或直接写入数据库的方式绕过问题。
18.8 任务卡在恢复状态
检查：
- 当前阶段 lease 是否过期；
- 是否已经记录 TIMED_OUT 事件；
- 是否存在重复事件；
- 是否已经执行恢复操作；
- 当前 attempt 是否达到最大次数；
- 数据库事务是否提交；
- Worker 进程是否仍然存活。
19. 回滚和清理
19.1 代码回滚
代码回滚应使用 Git 发布版本、标签或已验证提交。
不要在共享分支上执行：
git reset --hard
git push --force
回滚前应：
- 保存当前日志；
- 保存当前数据库状态；
- 确认代码版本；
- 确认数据库版本兼容性；
- 确认迁移是否可逆。
19.2 数据库回滚
数据库回滚必须由数据库管理员按照对应迁移脚本的回滚说明执行。
不得直接：
DROP DATABASE satellite_data_server_dev;
除非已经明确确认这是一次可丢弃的测试数据库。
不得直接删除或清空：
st_island_info
sd_observe_data
19.3 Worker 工作目录清理
Worker 工作目录可以按部署策略清理，但必须保留：
- 正在运行任务；
- 尚未登记的输出；
- 故障排查所需日志；
- 审计需要的执行记录。
清理前应确认任务已经完成或失败终止。
20. 部署验收清单
20.1 代码
- 已拉取指定远端分支；
- git status --short 无非预期源代码修改；
- Java 代码可以编译；
- htht-multisource 测试通过；
- htht-admin 测试通过；
- Worker 测试通过；
- 未提交 .m2、.tools、.pytest-tmp 等缓存。
20.2 数据库
- PostgreSQL 服务正常；
- PostGIS 扩展可用；
- 目标数据库名称正确；
- 应用数据库用户已创建；
- 数据库备份已完成；
- 多源迁移已按顺序执行；
- 多源核心表已创建；
- rs_batch_product 已创建；
- rs_task_event.execution_id 已创建；
- 产品能力种子数据已存在；
- st_island_info 未被修改；
- sd_observe_data 未被修改。
20.3 Java 服务
- JDK 17 可用；
- 数据库连接配置正确；
- Worker 命令配置正确；
- Worker 工作目录可写；
- 输入目录可读；
- 输出目录可写；
- Java 服务启动成功；
- 日志无持续报错；
- 健康检查可用。
20.4 Worker
- Python 虚拟环境已创建；
- Python 依赖安装完成；
- GDAL/HDF 等系统依赖可用；
- Worker 单元测试通过；
- Worker 入口命令可执行；
- Java 服务账户可以调用 Worker；
- 样例数据处理成功或已记录无法执行的原因。
20.5 接口
- 未登录访问被拒绝；
- 已登录用户可以查询自己的任务；
- 非任务所有者不能访问任务；
- 可以调度任务；
- 可以取消任务；
- 可以重试允许重试的阶段；
- 可以查询任务进度；
- 请求不能提交物理路径；
- 请求不能提交 Worker 命令；
- 请求不能提交执行授权；
- 请求不能覆盖任务计划快照。
21. 当前实现边界
当前版本明确不包含：
- 前端实现；
- 前后端联调；
- 分布式队列；
- 多节点调度；
- 远程 Worker 集群；
- 自动扩缩容；
- 生产级对象存储适配；
- 自动化生产数据库迁移发布；
- 完整的真实样例算法覆盖。
当前本地 Worker 调用路径是同步调用。应用层保留了事件和执行状态边界，未来可以替换为队列或远程 Worker，但当前部署不应默认具备分布式调度能力。
当前任务重试策略为单个阶段最多三次总尝试：
初始执行：attempt = 1
第一次重试：attempt = 2
第二次重试：attempt = 3
超过三次后不应继续自动重试。
22. 最终说明
本项目远端分支已经包含：
- Java 后端源代码；
- 多源业务模块；
- Admin 后端接口；
- Worker 源代码；
- 数据库迁移脚本；
- 后端配置模板；
- 测试代码；
- 架构设计和实施计划。
但是，部署能否成功还取决于外部环境是否正确准备，包括：
- PostgreSQL/PostGIS；
- 数据库权限；
- JDK 17；
- Maven 依赖；
- Python Worker 依赖；
- GDAL/HDF 等系统库；
- 输入数据；
- 输出目录；
- Worker 命令；
- 认证配置；
- 生产环境密钥。
因此，部署验收必须同时满足：
代码版本正确
数据库版本正确
Worker 环境正确
配置参数正确
认证机制可用
接口验证通过
输入和输出目录权限正确
只有这些条件全部满足后，才能认定后端部署完成。
```

## Flyway 数据库迁移

后端现在使用 PostgreSQL + Flyway 管理增量迁移。Flyway 迁移源码位于 `script/sql/migrations/`，构建时会复制到 `htht-admin` 的 `classpath:db/migration`。

### 新数据库

1. 创建 PostgreSQL 数据库。
2. 执行 `script/sql/pgsql.sql`，建立基础业务表和初始数据。
3. 使用新库专用命令启动后端。这只适用于刚导入基础 dump 的新库，作用是自动登记 version `0` baseline 后执行全部增量迁移：

```powershell
java -jar htht-admin\target\htht-admin.jar `
  --spring.profiles.active=dev `
  --spring.flyway.baseline-on-migrate=true `
  --spring.flyway.baseline-version=0
```

4. 启动日志显示迁移完成后，检查：

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

### 已有数据库

1. 先执行 PostgreSQL 备份。
2. 确认当前数据库结构已经包含仓库最新版本 `20260824.01` 的内容。
3. 在目标数据库执行 `script/sql/migrations/baseline-existing-postgres.sql` 一次。
4. 启动新版本后端；后续新版本迁移会自动执行。

应用默认配置保持 `spring.flyway.baseline-on-migrate=false`。没有登记过的非空数据库会直接启动失败，必须由部署人员显式 baseline，避免误把错误数据库当成已升级数据库。只有新库首次导入基础 dump 时，才通过启动参数临时开启 version `0` baseline。

### 迁移失败处理

- `validate-on-migrate` 发现 checksum 不一致时，禁止直接编辑已执行的迁移文件；恢复原文件或核对历史后，用新版本迁移修复。
- 迁移失败会阻止应用启动。修复数据库或迁移 SQL 后重新启动，Flyway 会重试失败版本。
- `clean-disabled=true`，生产环境禁止使用 `flyway clean`。
- 查看迁移状态：

```sql
SELECT installed_rank, version, description, type, checksum, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Worker 授权和过期恢复

每次 Worker 操作都会在调用前生成新的文件授权。预检、任务建计划和阶段执行不会复用其他操作的授权，也不会把 Worker grant 持久化到预检会话中。

当 Worker 明确返回 `INVALID_ASSET_MANIFEST` 且消息为 `manifest is expired` 时，当前操作会刷新授权并自动重试一次。其他 Worker 错误不会自动重试；第二次尝试仍失败时，接口只向用户返回稳定的业务提示，详细错误和 `requestId` 保留在后端日志中。前端主流程只需点击一次“开始处理”，系统会自动完成预检、建任务和启动。
