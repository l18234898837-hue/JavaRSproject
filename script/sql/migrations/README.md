# PostgreSQL Flyway 迁移

本目录是 PostgreSQL 增量迁移的源码目录。构建 `htht-admin` 时，所有 `V*.sql` 文件会被复制到 `classpath:db/migration`，由 Flyway 在应用启动时按版本顺序执行。

迁移文件一旦进入任意环境的 `flyway_schema_history`，禁止修改文件内容或文件名；修正必须新增更高版本的迁移。`README.md` 和 baseline 模板不会被复制到运行时迁移目录。

当前仓库最新迁移版本为 `20260827.02`（`V20260827_02__seed_fy3h_mersi_l1_profile.sql`）。

## 新数据库

先创建数据库并加载基础业务库：

```powershell
psql -h 127.0.0.1 -U <user> -d <database> -f script/sql/pgsql.sql
```

然后使用新库专用参数启动 `htht-admin`。由于基础 dump 已经让 schema 非空，Flyway 会先自动写入 version `0` 的 baseline，再执行所有 `V*.sql` 迁移：

```powershell
java -jar htht-admin\target\htht-admin.jar `
  --spring.profiles.active=dev `
  --spring.flyway.baseline-on-migrate=true `
  --spring.flyway.baseline-version=0
```

检查结果：

```sql
SELECT installed_rank, version, description, type, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 已有数据库显式 Baseline

已有库不要开启 `baseline-on-migrate`，也不要把基础 dump 重新导入。先备份数据库，确认当前库已经包含 `20260824.01` 所代表的结构，然后执行 `baseline-existing-postgres.sql` 中的 SQL，将当前状态登记为 Flyway baseline。之后使用默认配置启动应用，只有版本高于 `20260824.01` 的新迁移会执行。

baseline SQL 对已经存在相同成功 baseline 的数据库是 no-op；如果历史表已经存在其他版本记录，执行前必须先检查并由部署人员决定正确 baseline 版本，不能直接覆盖历史。

## 迁移规范

- 文件名使用 `V<数字版本>__<描述>.sql`，例如 `V20260825_01__add_column.sql`。
- 每个迁移只执行一次，SQL 应向前兼容并在文件头写明依赖和回滚说明。
- 不使用 `DROP DATABASE`、`flyway clean` 或无条件删除业务表。
- 迁移失败会阻止应用启动；修复失败迁移后再重新启动。
