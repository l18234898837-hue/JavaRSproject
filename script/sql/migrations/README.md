# 多源处理数据库迁移

本目录存放 `htht-multisource` 的显式版本化 PostgreSQL 迁移脚本。

骨架阶段不执行建表。首个脚本应创建 `rs_` 表、约束、索引和 `rs_schema_migration`，并由部署流程在 PostgreSQL advisory lock 下执行。
