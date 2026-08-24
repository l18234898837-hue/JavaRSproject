/*
 Navicat Premium Data Transfer

 Source Server         : 192.168.8.25
 Source Server Type    : PostgreSQL
 Source Server Version : 140004 (140004)
 Source Host           : 192.168.8.25:5432
 Source Catalog        : htht-server
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 140004 (140004)
 File Encoding         : 65001

 Date: 11/07/2024 18:25:32
*/


-- ----------------------------
-- Sequence structure for native
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."native";
CREATE SEQUENCE "public"."native"
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Table structure for pj_app_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_app_info";
CREATE TABLE "public"."pj_app_info" (
  "id" int8 NOT NULL,
  "app_name" varchar(255) COLLATE "pg_catalog"."default",
  "current_server" varchar(255) COLLATE "pg_catalog"."default",
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "password" varchar(255) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Records of pj_app_info
-- ----------------------------
INSERT INTO "public"."pj_app_info" VALUES (1, 'htht-worker', '192.168.8.25:10010', '2023-06-13 16:32:59.263', '2024-06-26 11:05:42.027', '123456');

-- ----------------------------
-- Table structure for pj_container_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_container_info";
CREATE TABLE "public"."pj_container_info" (
  "id" int8 NOT NULL,
  "app_id" int8,
  "container_name" varchar(255) COLLATE "pg_catalog"."default",
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "last_deploy_time" timestamp(6),
  "source_info" varchar(255) COLLATE "pg_catalog"."default",
  "source_type" int4,
  "status" int4,
  "version" varchar(255) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Records of pj_container_info
-- ----------------------------

-- ----------------------------
-- Table structure for pj_instance_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_instance_info";
CREATE TABLE "public"."pj_instance_info" (
  "id" int8 NOT NULL,
  "actual_trigger_time" int8,
  "app_id" int8,
  "expected_trigger_time" int8,
  "finished_time" int8,
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "instance_id" int8,
  "instance_params" oid,
  "job_id" int8,
  "job_params" oid,
  "last_report_time" int8,
  "result" oid,
  "running_times" int8,
  "status" int4,
  "task_tracker_address" varchar(255) COLLATE "pg_catalog"."default",
  "type" int4,
  "wf_instance_id" int8
)
;

-- ----------------------------
-- Records of pj_instance_info
-- ----------------------------

-- ----------------------------
-- Table structure for pj_job_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_job_info";
CREATE TABLE "public"."pj_job_info" (
  "id" int8 NOT NULL,
  "alarm_config" varchar(255) COLLATE "pg_catalog"."default",
  "app_id" int8,
  "concurrency" int4,
  "designated_workers" varchar(255) COLLATE "pg_catalog"."default",
  "dispatch_strategy" int4,
  "execute_type" int4,
  "extra" varchar(255) COLLATE "pg_catalog"."default",
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "instance_retry_num" int4,
  "instance_time_limit" int8,
  "job_description" varchar(255) COLLATE "pg_catalog"."default",
  "job_name" varchar(255) COLLATE "pg_catalog"."default",
  "job_params" oid,
  "lifecycle" varchar(255) COLLATE "pg_catalog"."default",
  "log_config" varchar(255) COLLATE "pg_catalog"."default",
  "max_instance_num" int4,
  "max_worker_count" int4,
  "min_cpu_cores" float8 NOT NULL,
  "min_disk_space" float8 NOT NULL,
  "min_memory_space" float8 NOT NULL,
  "next_trigger_time" int8,
  "notify_user_ids" varchar(255) COLLATE "pg_catalog"."default",
  "processor_info" varchar(255) COLLATE "pg_catalog"."default",
  "processor_type" int4,
  "status" int4,
  "tag" varchar(255) COLLATE "pg_catalog"."default",
  "task_retry_num" int4,
  "time_expression" varchar(255) COLLATE "pg_catalog"."default",
  "time_expression_type" int4
)
;

-- ----------------------------
-- Records of pj_job_info
-- ----------------------------
INSERT INTO "public"."pj_job_info" VALUES (1, '{"alertThreshold":0,"silenceWindowLen":0,"statisticWindowLen":0}', 1, 5, '', 2, 1, NULL, '2023-06-02 15:01:27.717', '2023-07-04 17:22:12.374', 1, 0, '', '单机处理器执行测试', NULL, '{}', '{"type":1}', 0, 0, 0, 0, 0, NULL, NULL, 'org.dromara.job.processors.StandaloneProcessorDemo', 1, 2, NULL, 1, '30000', 3);
INSERT INTO "public"."pj_job_info" VALUES (2, '{"alertThreshold":0,"silenceWindowLen":0,"statisticWindowLen":0}', 1, 5, '', 1, 2, NULL, '2023-06-02 15:04:45.342', '2023-07-04 17:22:12.816', 0, 0, NULL, '广播处理器测试', NULL, '{}', '{"type":1}', 0, 0, 0, 0, 0, NULL, NULL, 'org.dromara.job.processors.BroadcastProcessorDemo', 1, 2, NULL, 1, '30000', 3);
INSERT INTO "public"."pj_job_info" VALUES (3, '{"alertThreshold":0,"silenceWindowLen":0,"statisticWindowLen":0}', 1, 5, '', 1, 4, NULL, '2023-06-02 15:13:23.519', '2023-06-02 16:03:22.421', 0, 0, NULL, 'Map处理器测试', NULL, '{}', '{"type":1}', 0, 0, 0, 0, 0, NULL, NULL, 'org.dromara.job.processors.MapProcessorDemo', 1, 2, NULL, 1, '1000', 3);
INSERT INTO "public"."pj_job_info" VALUES (4, '{"alertThreshold":0,"silenceWindowLen":0,"statisticWindowLen":0}', 1, 5, '', 1, 3, NULL, '2023-06-02 15:45:25.896', '2023-06-02 16:03:23.125', 0, 0, NULL, 'MapReduce处理器测试', NULL, '{}', '{"type":1}', 0, 0, 0, 0, 0, NULL, NULL, 'org.dromara.job.processors.MapReduceProcessorDemo', 1, 2, NULL, 1, '1000', 3);

-- ----------------------------
-- Table structure for pj_oms_lock
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_oms_lock";
CREATE TABLE "public"."pj_oms_lock" (
  "id" int8 NOT NULL,
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "lock_name" varchar(255) COLLATE "pg_catalog"."default",
  "max_lock_time" int8,
  "ownerip" varchar(255) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Records of pj_oms_lock
-- ----------------------------

-- ----------------------------
-- Table structure for pj_server_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_server_info";
CREATE TABLE "public"."pj_server_info" (
  "id" int8 NOT NULL,
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "ip" varchar(255) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Records of pj_server_info
-- ----------------------------
INSERT INTO "public"."pj_server_info" VALUES (10, '2024-06-26 11:02:19.554', '2024-07-11 18:17:25.622', '192.168.8.25');
INSERT INTO "public"."pj_server_info" VALUES (2, '2024-06-26 09:46:26.546', '2024-06-26 11:13:08.47', '192.168.8.252');
INSERT INTO "public"."pj_server_info" VALUES (28, '2024-07-10 10:45:45.358', '2024-07-10 16:56:53.004', '192.168.0.110');

-- ----------------------------
-- Table structure for pj_user_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_user_info";
CREATE TABLE "public"."pj_user_info" (
  "id" int8 NOT NULL,
  "email" varchar(255) COLLATE "pg_catalog"."default",
  "extra" varchar(255) COLLATE "pg_catalog"."default",
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "password" varchar(255) COLLATE "pg_catalog"."default",
  "phone" varchar(255) COLLATE "pg_catalog"."default",
  "username" varchar(255) COLLATE "pg_catalog"."default",
  "web_hook" varchar(255) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Records of pj_user_info
-- ----------------------------

-- ----------------------------
-- Table structure for pj_workflow_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_workflow_info";
CREATE TABLE "public"."pj_workflow_info" (
  "id" int8 NOT NULL,
  "app_id" int8,
  "extra" varchar(255) COLLATE "pg_catalog"."default",
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "lifecycle" varchar(255) COLLATE "pg_catalog"."default",
  "max_wf_instance_num" int4,
  "next_trigger_time" int8,
  "notify_user_ids" varchar(255) COLLATE "pg_catalog"."default",
  "pedag" oid,
  "status" int4,
  "time_expression" varchar(255) COLLATE "pg_catalog"."default",
  "time_expression_type" int4,
  "wf_description" varchar(255) COLLATE "pg_catalog"."default",
  "wf_name" varchar(255) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Records of pj_workflow_info
-- ----------------------------

-- ----------------------------
-- Table structure for pj_workflow_instance_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_workflow_instance_info";
CREATE TABLE "public"."pj_workflow_instance_info" (
  "id" int8 NOT NULL,
  "actual_trigger_time" int8,
  "app_id" int8,
  "dag" oid,
  "expected_trigger_time" int8,
  "finished_time" int8,
  "gmt_create" timestamp(6),
  "gmt_modified" timestamp(6),
  "parent_wf_instance_id" int8,
  "result" oid,
  "status" int4,
  "wf_context" oid,
  "wf_init_params" oid,
  "wf_instance_id" int8,
  "workflow_id" int8
)
;

-- ----------------------------
-- Records of pj_workflow_instance_info
-- ----------------------------

-- ----------------------------
-- Table structure for pj_workflow_node_info
-- ----------------------------
DROP TABLE IF EXISTS "public"."pj_workflow_node_info";
CREATE TABLE "public"."pj_workflow_node_info" (
  "id" int8 NOT NULL,
  "app_id" int8 NOT NULL,
  "enable" bool NOT NULL,
  "extra" oid,
  "gmt_create" timestamp(6) NOT NULL,
  "gmt_modified" timestamp(6) NOT NULL,
  "job_id" int8,
  "node_name" varchar(255) COLLATE "pg_catalog"."default",
  "node_params" oid,
  "skip_when_failed" bool NOT NULL,
  "type" int4,
  "workflow_id" int8
)
;

-- ----------------------------
-- Records of pj_workflow_node_info
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_config";
CREATE TABLE "public"."sys_config" (
  "config_id" int8 NOT NULL,
  "config_name" varchar(100) COLLATE "pg_catalog"."default",
  "config_key" varchar(100) COLLATE "pg_catalog"."default",
  "config_value" varchar(500) COLLATE "pg_catalog"."default",
  "config_type" char(1) COLLATE "pg_catalog"."default" DEFAULT 'N',
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_config"."config_id" IS '参数主键';
COMMENT ON COLUMN "public"."sys_config"."config_name" IS '参数名称';
COMMENT ON COLUMN "public"."sys_config"."config_key" IS '参数键名';
COMMENT ON COLUMN "public"."sys_config"."config_value" IS '参数键值';
COMMENT ON COLUMN "public"."sys_config"."config_type" IS '系统内置（Y是 N否）';
COMMENT ON COLUMN "public"."sys_config"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_config"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_config"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_config"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_config"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_config" IS '参数配置表';

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO "public"."sys_config" VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', '2023-03-15 14:06:16', '', NULL, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
INSERT INTO "public"."sys_config" VALUES (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2023-03-15 14:06:16', '', NULL, '初始化密码 123456');
INSERT INTO "public"."sys_config" VALUES (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', '2023-03-15 14:06:16', '', NULL, '深色主题theme-dark，浅色主题theme-light');
INSERT INTO "public"."sys_config" VALUES (4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'true', 'Y', 'admin', '2023-03-15 14:06:16', '', NULL, '是否开启验证码功能（true开启，false关闭）');
INSERT INTO "public"."sys_config" VALUES (5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'false', 'Y', 'admin', '2023-03-15 14:06:16', '', NULL, '是否开启注册用户功能（true开启，false关闭）');
INSERT INTO "public"."sys_config" VALUES (11, 'OSS预览列表资源开关', 'sys.oss.previewListResource', 'true', 'Y', 'admin', '2023-03-15 14:06:16', '', NULL, 'true:开启, false:关闭');

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dept";
CREATE TABLE "public"."sys_dept" (
  "dept_id" int8 NOT NULL,
  "parent_id" int8 DEFAULT 0,
  "ancestors" varchar(500) COLLATE "pg_catalog"."default",
  "dept_name" varchar(30) COLLATE "pg_catalog"."default",
  "order_num" int4 DEFAULT 0,
  "leader" varchar(20) COLLATE "pg_catalog"."default",
  "phone" varchar(11) COLLATE "pg_catalog"."default",
  "email" varchar(50) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."sys_dept"."dept_id" IS '部门id';
COMMENT ON COLUMN "public"."sys_dept"."parent_id" IS '父部门id';
COMMENT ON COLUMN "public"."sys_dept"."ancestors" IS '祖级列表';
COMMENT ON COLUMN "public"."sys_dept"."dept_name" IS '部门名称';
COMMENT ON COLUMN "public"."sys_dept"."order_num" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_dept"."leader" IS '负责人';
COMMENT ON COLUMN "public"."sys_dept"."phone" IS '联系电话';
COMMENT ON COLUMN "public"."sys_dept"."email" IS '邮箱';
COMMENT ON COLUMN "public"."sys_dept"."status" IS '部门状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_dept"."del_flag" IS '删除标志（0代表存在 2代表删除）';
COMMENT ON COLUMN "public"."sys_dept"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_dept"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_dept"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_dept"."update_time" IS '更新时间';
COMMENT ON TABLE "public"."sys_dept" IS '部门表';

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO "public"."sys_dept" VALUES (101, 100, '0,100', '广东公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:23');
INSERT INTO "public"."sys_dept" VALUES (103, 101, '0,100,101', '研发部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:23');
INSERT INTO "public"."sys_dept" VALUES (104, 101, '0,100,101', '市场部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:23');
INSERT INTO "public"."sys_dept" VALUES (105, 101, '0,100,101', '测试部门', 3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:23');
INSERT INTO "public"."sys_dept" VALUES (106, 101, '0,100,101', '财务部门', 4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:23');
INSERT INTO "public"."sys_dept" VALUES (107, 101, '0,100,101', '运维部门', 5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:23');
INSERT INTO "public"."sys_dept" VALUES (108, 102, '0,100,102', '市场部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:29');
INSERT INTO "public"."sys_dept" VALUES (109, 102, '0,100,102', '财务部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2024-06-27 15:02:09.652');
INSERT INTO "public"."sys_dept" VALUES (100, 0, '0', '广东HTHT', 0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-06-06 14:08:07');
INSERT INTO "public"."sys_dept" VALUES (102, 100, '0,100', '深圳公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:03:29');

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dict_data";
CREATE TABLE "public"."sys_dict_data" (
  "dict_code" int8 NOT NULL,
  "dict_sort" int4 DEFAULT 0,
  "dict_label" varchar(100) COLLATE "pg_catalog"."default",
  "dict_value" varchar(100) COLLATE "pg_catalog"."default",
  "dict_type" varchar(100) COLLATE "pg_catalog"."default",
  "css_class" varchar(100) COLLATE "pg_catalog"."default",
  "list_class" varchar(100) COLLATE "pg_catalog"."default",
  "is_default" char(1) COLLATE "pg_catalog"."default" DEFAULT 'N',
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_dict_data"."dict_code" IS '字典编码';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_sort" IS '字典排序';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_label" IS '字典标签';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_value" IS '字典键值';
COMMENT ON COLUMN "public"."sys_dict_data"."dict_type" IS '字典类型';
COMMENT ON COLUMN "public"."sys_dict_data"."css_class" IS '样式属性（其他样式扩展）';
COMMENT ON COLUMN "public"."sys_dict_data"."list_class" IS '表格回显样式';
COMMENT ON COLUMN "public"."sys_dict_data"."is_default" IS '是否默认（Y是 N否）';
COMMENT ON COLUMN "public"."sys_dict_data"."status" IS '状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_dict_data"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_dict_data"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_dict_data"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_dict_data"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_dict_data"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_dict_data" IS '字典数据表';

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO "public"."sys_dict_data" VALUES (1, 1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '性别男');
INSERT INTO "public"."sys_dict_data" VALUES (2, 2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '性别女');
INSERT INTO "public"."sys_dict_data" VALUES (3, 3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '性别未知');
INSERT INTO "public"."sys_dict_data" VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '显示菜单');
INSERT INTO "public"."sys_dict_data" VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '隐藏菜单');
INSERT INTO "public"."sys_dict_data" VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '正常状态');
INSERT INTO "public"."sys_dict_data" VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '停用状态');
INSERT INTO "public"."sys_dict_data" VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '系统默认是');
INSERT INTO "public"."sys_dict_data" VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '系统默认否');
INSERT INTO "public"."sys_dict_data" VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '通知');
INSERT INTO "public"."sys_dict_data" VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '公告');
INSERT INTO "public"."sys_dict_data" VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '正常状态');
INSERT INTO "public"."sys_dict_data" VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '关闭状态');
INSERT INTO "public"."sys_dict_data" VALUES (18, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '新增操作');
INSERT INTO "public"."sys_dict_data" VALUES (19, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '修改操作');
INSERT INTO "public"."sys_dict_data" VALUES (20, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '删除操作');
INSERT INTO "public"."sys_dict_data" VALUES (21, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '授权操作');
INSERT INTO "public"."sys_dict_data" VALUES (22, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '导出操作');
INSERT INTO "public"."sys_dict_data" VALUES (23, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '导入操作');
INSERT INTO "public"."sys_dict_data" VALUES (24, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '强退操作');
INSERT INTO "public"."sys_dict_data" VALUES (25, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '生成操作');
INSERT INTO "public"."sys_dict_data" VALUES (26, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '清空操作');
INSERT INTO "public"."sys_dict_data" VALUES (27, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '正常状态');
INSERT INTO "public"."sys_dict_data" VALUES (28, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '停用状态');
INSERT INTO "public"."sys_dict_data" VALUES (29, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '其他操作');

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_dict_type";
CREATE TABLE "public"."sys_dict_type" (
  "dict_id" int8 NOT NULL,
  "dict_name" varchar(100) COLLATE "pg_catalog"."default",
  "dict_type" varchar(100) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_dict_type"."dict_id" IS '字典主键';
COMMENT ON COLUMN "public"."sys_dict_type"."dict_name" IS '字典名称';
COMMENT ON COLUMN "public"."sys_dict_type"."dict_type" IS '字典类型';
COMMENT ON COLUMN "public"."sys_dict_type"."status" IS '状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_dict_type"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_dict_type"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_dict_type"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_dict_type"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_dict_type"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_dict_type" IS '字典类型表';

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO "public"."sys_dict_type" VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '用户性别列表');
INSERT INTO "public"."sys_dict_type" VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '菜单状态列表');
INSERT INTO "public"."sys_dict_type" VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-06 14:32:13', '系统开关列表');
INSERT INTO "public"."sys_dict_type" VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '系统是否列表');
INSERT INTO "public"."sys_dict_type" VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '通知类型列表');
INSERT INTO "public"."sys_dict_type" VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '通知状态列表');
INSERT INTO "public"."sys_dict_type" VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '操作类型列表');
INSERT INTO "public"."sys_dict_type" VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2023-03-30 14:17:50', '登录状态列表');

-- ----------------------------
-- Table structure for sys_file
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_file";
CREATE TABLE "public"."sys_file" (
  "id" int8 NOT NULL,
  "file_name" varchar(100) COLLATE "pg_catalog"."default",
  "extension" varchar(20) COLLATE "pg_catalog"."default",
  "total_size" int8,
  "file_path" varchar(1024) COLLATE "pg_catalog"."default",
  "is_static" varchar(1) COLLATE "pg_catalog"."default",
  "static_path" varchar(1024) COLLATE "pg_catalog"."default",
  "create_by" varchar(40) COLLATE "pg_catalog"."default",
  "update_by" varchar(40) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."sys_file"."id" IS '主键ID';
COMMENT ON COLUMN "public"."sys_file"."file_name" IS '文件名';
COMMENT ON COLUMN "public"."sys_file"."extension" IS '扩展名';
COMMENT ON COLUMN "public"."sys_file"."total_size" IS '文件大小';
COMMENT ON COLUMN "public"."sys_file"."file_path" IS '文件路径';
COMMENT ON COLUMN "public"."sys_file"."is_static" IS '是否是静态文件（0：否，1：是）';
COMMENT ON COLUMN "public"."sys_file"."static_path" IS '静态文件相对路径';
COMMENT ON COLUMN "public"."sys_file"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_file"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_file"."create_time" IS '创建日期';
COMMENT ON COLUMN "public"."sys_file"."update_time" IS '更新时间';

-- ----------------------------
-- Records of sys_file
-- ----------------------------
INSERT INTO "public"."sys_file" VALUES (1809066365269393409, '2', 'jpg', 406126, 'D:/filestorage/static/jpg/cc07cf8916944057851104f923a2140c.jpg', '1', 'jpg/cc07cf8916944057851104f923a2140c.jpg', NULL, NULL, '2024-07-05 11:26:46.971', '2024-07-05 11:26:46.971');

-- ----------------------------
-- Table structure for sys_logininfor
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_logininfor";
CREATE TABLE "public"."sys_logininfor" (
  "info_id" int8 NOT NULL,
  "user_name" varchar(50) COLLATE "pg_catalog"."default",
  "ipaddr" varchar(128) COLLATE "pg_catalog"."default",
  "login_location" varchar(255) COLLATE "pg_catalog"."default",
  "browser" varchar(50) COLLATE "pg_catalog"."default",
  "os" varchar(50) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "msg" varchar(255) COLLATE "pg_catalog"."default",
  "login_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."sys_logininfor"."info_id" IS '访问ID';
COMMENT ON COLUMN "public"."sys_logininfor"."user_name" IS '用户账号';
COMMENT ON COLUMN "public"."sys_logininfor"."ipaddr" IS '登录IP地址';
COMMENT ON COLUMN "public"."sys_logininfor"."login_location" IS '登录地点';
COMMENT ON COLUMN "public"."sys_logininfor"."browser" IS '浏览器类型';
COMMENT ON COLUMN "public"."sys_logininfor"."os" IS '操作系统';
COMMENT ON COLUMN "public"."sys_logininfor"."status" IS '登录状态（0成功 1失败）';
COMMENT ON COLUMN "public"."sys_logininfor"."msg" IS '提示消息';
COMMENT ON COLUMN "public"."sys_logininfor"."login_time" IS '访问时间';
COMMENT ON TABLE "public"."sys_logininfor" IS '系统访问记录';

-- ----------------------------
-- Records of sys_logininfor
-- ----------------------------
INSERT INTO "public"."sys_logininfor" VALUES (1804425028217524225, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-22 16:03:45.98');
INSERT INTO "public"."sys_logininfor" VALUES (1805534528483807234, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-25 17:32:31.456');
INSERT INTO "public"."sys_logininfor" VALUES (1805797561705758721, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 10:57:43.457');
INSERT INTO "public"."sys_logininfor" VALUES (1805872440380858369, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 15:55:15.928');
INSERT INTO "public"."sys_logininfor" VALUES (1805872467933241346, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-26 15:55:22.499');
INSERT INTO "public"."sys_logininfor" VALUES (1805872746246283265, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 15:56:28.855');
INSERT INTO "public"."sys_logininfor" VALUES (1805872753837973506, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-26 15:56:30.664');
INSERT INTO "public"."sys_logininfor" VALUES (1805872770468388866, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 15:56:34.629');
INSERT INTO "public"."sys_logininfor" VALUES (1805873109800165378, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 15:57:55.529');
INSERT INTO "public"."sys_logininfor" VALUES (1805873232693272577, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 15:58:24.831');
INSERT INTO "public"."sys_logininfor" VALUES (1805873525296308225, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 15:59:34.594');
INSERT INTO "public"."sys_logininfor" VALUES (1805873605726281730, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 15:59:53.77');
INSERT INTO "public"."sys_logininfor" VALUES (1805874136578367489, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:02:00.334');
INSERT INTO "public"."sys_logininfor" VALUES (1805874239816966146, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-26 16:02:24.95');
INSERT INTO "public"."sys_logininfor" VALUES (1805874250898317313, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:02:27.592');
INSERT INTO "public"."sys_logininfor" VALUES (1805875051565785090, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-26 16:05:38.485');
INSERT INTO "public"."sys_logininfor" VALUES (1805875058205368321, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:05:40.068');
INSERT INTO "public"."sys_logininfor" VALUES (1805875614890172418, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:07:52.792');
INSERT INTO "public"."sys_logininfor" VALUES (1805875735019233282, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:08:21.434');
INSERT INTO "public"."sys_logininfor" VALUES (1805875990473318401, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:09:22.339');
INSERT INTO "public"."sys_logininfor" VALUES (1805876408205025282, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:11:01.934');
INSERT INTO "public"."sys_logininfor" VALUES (1805876932174258177, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:13:06.858');
INSERT INTO "public"."sys_logininfor" VALUES (1805876988482789377, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:13:20.282');
INSERT INTO "public"."sys_logininfor" VALUES (1805877545079513089, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:15:32.985');
INSERT INTO "public"."sys_logininfor" VALUES (1805877952698753025, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:17:10.17');
INSERT INTO "public"."sys_logininfor" VALUES (1805878899353165826, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:20:55.87');
INSERT INTO "public"."sys_logininfor" VALUES (1805879486056603649, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:23:15.75');
INSERT INTO "public"."sys_logininfor" VALUES (1805880298057080834, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:26:29.346');
INSERT INTO "public"."sys_logininfor" VALUES (1805880642552045570, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:27:51.481');
INSERT INTO "public"."sys_logininfor" VALUES (1805881959659970562, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:33:05.503');
INSERT INTO "public"."sys_logininfor" VALUES (1805882024310972417, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:33:20.917');
INSERT INTO "public"."sys_logininfor" VALUES (1805882113339269121, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:33:42.143');
INSERT INTO "public"."sys_logininfor" VALUES (1805883025675886594, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:37:19.662');
INSERT INTO "public"."sys_logininfor" VALUES (1805883245490970625, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:38:12.069');
INSERT INTO "public"."sys_logininfor" VALUES (1805883682843631617, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:39:56.342');
INSERT INTO "public"."sys_logininfor" VALUES (1805884001757536258, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:41:12.377');
INSERT INTO "public"."sys_logininfor" VALUES (1805884072133763074, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:41:29.156');
INSERT INTO "public"."sys_logininfor" VALUES (1805884398169595906, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:42:46.889');
INSERT INTO "public"."sys_logininfor" VALUES (1805884605049446401, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:43:36.214');
INSERT INTO "public"."sys_logininfor" VALUES (1805884812831072257, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:44:25.752');
INSERT INTO "public"."sys_logininfor" VALUES (1805887455439724545, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:54:55.8');
INSERT INTO "public"."sys_logininfor" VALUES (1805887539820732417, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 16:55:15.918');
INSERT INTO "public"."sys_logininfor" VALUES (1805889396764942337, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 17:02:38.648');
INSERT INTO "public"."sys_logininfor" VALUES (1805904814476992514, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 18:03:54.516');
INSERT INTO "public"."sys_logininfor" VALUES (1805910022190620674, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-26 18:24:36.127');
INSERT INTO "public"."sys_logininfor" VALUES (1806159376693624833, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 10:55:26.875');
INSERT INTO "public"."sys_logininfor" VALUES (1806159431777419266, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 10:55:40.012');
INSERT INTO "public"."sys_logininfor" VALUES (1806159612526755841, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 10:56:23.106');
INSERT INTO "public"."sys_logininfor" VALUES (1806160177918935041, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-27 10:58:37.905');
INSERT INTO "public"."sys_logininfor" VALUES (1806160188241117186, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 10:58:40.367');
INSERT INTO "public"."sys_logininfor" VALUES (1806160251155677186, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-27 10:58:55.362');
INSERT INTO "public"."sys_logininfor" VALUES (1806160251382169602, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '1', '验证码错误', '2024-06-27 10:58:55.421');
INSERT INTO "public"."sys_logininfor" VALUES (1806160324899930114, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 10:59:12.949');
INSERT INTO "public"."sys_logininfor" VALUES (1806160625065295874, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:00:24.514');
INSERT INTO "public"."sys_logininfor" VALUES (1806160837997527041, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:01:15.28');
INSERT INTO "public"."sys_logininfor" VALUES (1806160940120440833, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:01:39.629');
INSERT INTO "public"."sys_logininfor" VALUES (1806161826389458946, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:05:10.932');
INSERT INTO "public"."sys_logininfor" VALUES (1806161890927214593, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:05:26.319');
INSERT INTO "public"."sys_logininfor" VALUES (1806162769201889281, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:08:55.717');
INSERT INTO "public"."sys_logininfor" VALUES (1806163181967540226, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:10:34.127');
INSERT INTO "public"."sys_logininfor" VALUES (1806165462507094018, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:19:37.85');
INSERT INTO "public"."sys_logininfor" VALUES (1806165927189839873, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:21:28.638');
INSERT INTO "public"."sys_logininfor" VALUES (1806167787250417665, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:28:52.113');
INSERT INTO "public"."sys_logininfor" VALUES (1806167107391488001, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:26:10.021');
INSERT INTO "public"."sys_logininfor" VALUES (1806167143139540994, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 11:26:18.544');
INSERT INTO "public"."sys_logininfor" VALUES (1806208426201612289, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 14:10:21.193');
INSERT INTO "public"."sys_logininfor" VALUES (1806231986576064514, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 15:43:58.424');
INSERT INTO "public"."sys_logininfor" VALUES (1806232144063791106, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 15:44:35.972');
INSERT INTO "public"."sys_logininfor" VALUES (1806233101422067713, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 15:48:24.224');
INSERT INTO "public"."sys_logininfor" VALUES (1806233225447636993, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 15:48:53.795');
INSERT INTO "public"."sys_logininfor" VALUES (1806259972025122818, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 17:35:10.676');
INSERT INTO "public"."sys_logininfor" VALUES (1806266069976879106, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 17:59:24.536');
INSERT INTO "public"."sys_logininfor" VALUES (1806271159521513473, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-27 18:19:37.982');
INSERT INTO "public"."sys_logininfor" VALUES (1806497588771295234, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 09:19:22.922');
INSERT INTO "public"."sys_logininfor" VALUES (1806500962598129666, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 09:32:47.305');
INSERT INTO "public"."sys_logininfor" VALUES (1806503747125252097, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 09:43:51.187');
INSERT INTO "public"."sys_logininfor" VALUES (1806514110885638145, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:25:02.099');
INSERT INTO "public"."sys_logininfor" VALUES (1806514336929263618, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:25:55.994');
INSERT INTO "public"."sys_logininfor" VALUES (1806514659810979841, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:27:12.975');
INSERT INTO "public"."sys_logininfor" VALUES (1806514904389234689, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:28:11.286');
INSERT INTO "public"."sys_logininfor" VALUES (1806515105044738049, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:28:59.127');
INSERT INTO "public"."sys_logininfor" VALUES (1806515155728707585, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:29:11.21');
INSERT INTO "public"."sys_logininfor" VALUES (1806515242508857346, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:29:31.899');
INSERT INTO "public"."sys_logininfor" VALUES (1806515289904492546, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:29:43.2');
INSERT INTO "public"."sys_logininfor" VALUES (1806515377741606913, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:30:04.143');
INSERT INTO "public"."sys_logininfor" VALUES (1806515618482073602, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:31:01.539');
INSERT INTO "public"."sys_logininfor" VALUES (1806515723541000193, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:31:26.588');
INSERT INTO "public"."sys_logininfor" VALUES (1806515916596424705, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:32:12.615');
INSERT INTO "public"."sys_logininfor" VALUES (1806516095751925761, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:32:55.33');
INSERT INTO "public"."sys_logininfor" VALUES (1806516507380920322, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:34:33.469');
INSERT INTO "public"."sys_logininfor" VALUES (1806516566151507970, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:34:47.481');
INSERT INTO "public"."sys_logininfor" VALUES (1806516700188880897, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 10:35:19.439');
INSERT INTO "public"."sys_logininfor" VALUES (1806558400772943874, 'admin', '192.168.8.252', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 13:21:01.633');
INSERT INTO "public"."sys_logininfor" VALUES (1806569939303247874, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 14:06:52.632');
INSERT INTO "public"."sys_logininfor" VALUES (1806585948244389890, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 15:10:29.462');
INSERT INTO "public"."sys_logininfor" VALUES (1806587484626333697, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 15:16:35.764');
INSERT INTO "public"."sys_logininfor" VALUES (1806595832960626689, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 15:49:46.162');
INSERT INTO "public"."sys_logininfor" VALUES (1806597098902237186, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 15:54:47.986');
INSERT INTO "public"."sys_logininfor" VALUES (1806598483693645826, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:00:18.146');
INSERT INTO "public"."sys_logininfor" VALUES (1806599437436432385, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:04:05.536');
INSERT INTO "public"."sys_logininfor" VALUES (1806599500137082882, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:04:20.485');
INSERT INTO "public"."sys_logininfor" VALUES (1806605101940453377, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:26:36.059');
INSERT INTO "public"."sys_logininfor" VALUES (1806605447660154882, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:27:58.485');
INSERT INTO "public"."sys_logininfor" VALUES (1806605499686301697, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:28:10.889');
INSERT INTO "public"."sys_logininfor" VALUES (1806612257888645121, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:55:02.17');
INSERT INTO "public"."sys_logininfor" VALUES (1806612446112231426, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:55:47.046');
INSERT INTO "public"."sys_logininfor" VALUES (1806613338320384002, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 16:59:19.765');
INSERT INTO "public"."sys_logininfor" VALUES (1806623031965102082, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 17:37:50.91');
INSERT INTO "public"."sys_logininfor" VALUES (1806627561826004994, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 17:55:50.912');
INSERT INTO "public"."sys_logininfor" VALUES (1806627581182717953, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 17:55:55.528');
INSERT INTO "public"."sys_logininfor" VALUES (1806627624002367490, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-28 17:56:05.737');
INSERT INTO "public"."sys_logininfor" VALUES (1806860642726031361, 'admin', '192.168.8.136', '内网IP', 'MSEdge', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-29 09:22:01.729');
INSERT INTO "public"."sys_logininfor" VALUES (1806860670257442818, 'admin', '192.168.8.136', '内网IP', 'MSEdge', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-06-29 09:22:08.294');
INSERT INTO "public"."sys_logininfor" VALUES (1806881620537487361, '李杰聪', '192.168.8.136', '内网IP', 'MSEdge', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-29 10:45:23.229');
INSERT INTO "public"."sys_logininfor" VALUES (1806881754386116610, '李杰聪', '192.168.8.136', '内网IP', 'MSEdge', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-29 10:45:55.142');
INSERT INTO "public"."sys_logininfor" VALUES (1806881790939475970, '李杰聪', '192.168.8.136', '内网IP', 'MSEdge', 'Windows 10 or Windows Server 2016', '1', '验证码已失效', '2024-06-29 10:46:03.857');
INSERT INTO "public"."sys_logininfor" VALUES (1807694867940302850, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 16:36:56.516');
INSERT INTO "public"."sys_logininfor" VALUES (1807695784072765442, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '1', '验证码错误', '2024-07-01 16:40:34.943');
INSERT INTO "public"."sys_logininfor" VALUES (1807695801751756801, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 16:40:39.158');
INSERT INTO "public"."sys_logininfor" VALUES (1807697260463579138, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '1', '验证码错误', '2024-07-01 16:46:26.942');
INSERT INTO "public"."sys_logininfor" VALUES (1807697269657493505, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 16:46:29.134');
INSERT INTO "public"."sys_logininfor" VALUES (1807703133713915905, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 17:09:47.232');
INSERT INTO "public"."sys_logininfor" VALUES (1807705707301101569, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '1', '验证码已失效', '2024-07-01 17:20:00.823');
INSERT INTO "public"."sys_logininfor" VALUES (1807705713223458818, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 17:20:02.237');
INSERT INTO "public"."sys_logininfor" VALUES (1807705783008288770, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 17:20:18.875');
INSERT INTO "public"."sys_logininfor" VALUES (1807707856332447746, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 17:28:33.193');
INSERT INTO "public"."sys_logininfor" VALUES (1807710919348772866, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-01 17:40:43.474');
INSERT INTO "public"."sys_logininfor" VALUES (1807975127177027585, 'admin', '192.168.8.42', '内网IP', 'Safari', 'iPhone', '0', '登录成功', '2024-07-02 11:10:35.529');
INSERT INTO "public"."sys_logininfor" VALUES (1807975682540625922, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-07-02 11:12:47.939');
INSERT INTO "public"."sys_logininfor" VALUES (1807976162155094018, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-07-02 11:14:42.287');
INSERT INTO "public"."sys_logininfor" VALUES (1808060444978503681, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-07-02 16:49:36.879');
INSERT INTO "public"."sys_logininfor" VALUES (1808749531578294273, 'admin', '192.168.8.42', '内网IP', 'Chrome', 'Windows 10 or Windows Server 2016', '0', '登录成功', '2024-07-04 14:27:47.925');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_menu";
CREATE TABLE "public"."sys_menu" (
  "menu_id" int8 NOT NULL,
  "menu_name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "parent_id" int8 DEFAULT 0,
  "order_num" int4 DEFAULT 0,
  "path" varchar(200) COLLATE "pg_catalog"."default",
  "component" varchar(255) COLLATE "pg_catalog"."default",
  "query_param" varchar(255) COLLATE "pg_catalog"."default",
  "is_frame" char(1) COLLATE "pg_catalog"."default" DEFAULT '1',
  "is_cache" char(1) COLLATE "pg_catalog"."default",
  "menu_type" char(1) COLLATE "pg_catalog"."default",
  "visible" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "perms" varchar(100) COLLATE "pg_catalog"."default",
  "icon" varchar(100) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default",
  "show_parent" char(8) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_menu"."menu_id" IS '菜单ID';
COMMENT ON COLUMN "public"."sys_menu"."menu_name" IS '菜单名称';
COMMENT ON COLUMN "public"."sys_menu"."parent_id" IS '父菜单ID';
COMMENT ON COLUMN "public"."sys_menu"."order_num" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_menu"."path" IS '路由地址';
COMMENT ON COLUMN "public"."sys_menu"."component" IS '组件路径';
COMMENT ON COLUMN "public"."sys_menu"."query_param" IS '路由参数';
COMMENT ON COLUMN "public"."sys_menu"."is_frame" IS '是否为外链（0是 1否）';
COMMENT ON COLUMN "public"."sys_menu"."is_cache" IS '是否缓存（0缓存 1不缓存）';
COMMENT ON COLUMN "public"."sys_menu"."menu_type" IS '菜单类型（M目录 C菜单 F按钮）';
COMMENT ON COLUMN "public"."sys_menu"."visible" IS '显示状态（0显示 1隐藏）';
COMMENT ON COLUMN "public"."sys_menu"."status" IS '菜单状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_menu"."perms" IS '权限标识';
COMMENT ON COLUMN "public"."sys_menu"."icon" IS '菜单图标';
COMMENT ON COLUMN "public"."sys_menu"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_menu"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_menu"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_menu"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_menu"."remark" IS '备注';
COMMENT ON COLUMN "public"."sys_menu"."show_parent" IS '是否显示父级';
COMMENT ON TABLE "public"."sys_menu" IS '菜单权限表';

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO "public"."sys_menu" VALUES (1, '系统管理', 0, 1, 'system', NULL, '', '1', '0', 'M', '0', '0', '', 'setting', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 17:17:14', '系统管理目录', '1       ');
INSERT INTO "public"."sys_menu" VALUES (101, '角色管理', 1, 2, '/system/role', '/system/role/index', '', '1', '1', 'C', '0', '0', 'system:role:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:13', '角色管理菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (102, '菜单管理', 1, 3, '/system/menu', '/system/menu/index', '', '1', '1', 'C', '0', '0', 'system:menu:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:18', '菜单管理菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (103, '部门管理', 1, 4, '/system/dept', '/system/dept/index', '', '1', '1', 'C', '0', '0', 'system:dept:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:23', '部门管理菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (104, '岗位管理', 1, 5, '/system/post', '/system/post/index', '', '1', '1', 'C', '0', '0', 'system:post:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:26', '岗位管理菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (105, '字典管理', 1, 6, '/system/dict', '/system/dict/index', '', '1', '1', 'C', '0', '0', 'system:dict:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:29', '字典管理菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (106, '参数设置', 1, 7, '/system/config', '/system/config/index', '', '1', '1', 'C', '0', '1', 'system:config:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:31', '参数设置菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (107, '通知公告', 1, 8, '/system/notice', '/system/notice/index', '', '1', '1', 'C', '0', '1', 'system:notice:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:34', '通知公告菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (108, '日志管理', 1, 9, 'log', '', '', '1', '0', 'M', '0', '0', '', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:37', '日志管理菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (118, '文件管理', 1, 10, '/system/oss', '/system/oss/index', '', '1', '1', 'C', '0', '1', 'system:oss:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 16:54:39', '文件管理菜单', '1       ');
INSERT INTO "public"."sys_menu" VALUES (1001, '用户查询', 100, 1, '', '', '', '1', '0', 'F', '0', '0', 'system:user:query', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1002, '用户新增', 100, 2, '', '', '', '1', '0', 'F', '0', '0', 'system:user:add', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1003, '用户修改', 100, 3, '', '', '', '1', '0', 'F', '0', '0', 'system:user:edit', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1004, '用户删除', 100, 4, '', '', '', '1', '0', 'F', '0', '0', 'system:user:remove', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1005, '用户导出', 100, 5, '', '', '', '1', '0', 'F', '0', '0', 'system:user:export', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1006, '用户导入', 100, 6, '', '', '', '1', '0', 'F', '0', '0', 'system:user:import', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1007, '重置密码', 100, 7, '', '', '', '1', '0', 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1008, '角色查询', 101, 1, '', '', '', '1', '0', 'F', '0', '0', 'system:role:query', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1009, '角色新增', 101, 2, '', '', '', '1', '0', 'F', '0', '0', 'system:role:add', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1010, '角色修改', 101, 3, '', '', '', '1', '0', 'F', '0', '0', 'system:role:edit', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1011, '角色删除', 101, 4, '', '', '', '1', '0', 'F', '0', '0', 'system:role:remove', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1012, '角色导出', 101, 5, '', '', '', '1', '0', 'F', '0', '0', 'system:role:export', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1013, '菜单查询', 102, 1, '', '', '', '1', '0', 'F', '0', '0', 'system:menu:query', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1014, '菜单新增', 102, 2, '', '', '', '1', '0', 'F', '0', '0', 'system:menu:add', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1015, '菜单修改', 102, 3, '', '', '', '1', '0', 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1016, '菜单删除', 102, 4, '', '', '', '1', '0', 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1017, '部门查询', 103, 1, '', '', '', '1', '0', 'F', '0', '0', 'system:dept:query', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1018, '部门新增', 103, 2, '', '', '', '1', '0', 'F', '0', '0', 'system:dept:add', '#', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1020, '部门删除', 103, 4, '', '', '', '1', '0', 'F', '0', '0', 'system:dept:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1021, '岗位查询', 104, 1, '', '', '', '1', '0', 'F', '0', '0', 'system:post:query', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1022, '岗位新增', 104, 2, '', '', '', '1', '0', 'F', '0', '0', 'system:post:add', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1023, '岗位修改', 104, 3, '', '', '', '1', '0', 'F', '0', '0', 'system:post:edit', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1024, '岗位删除', 104, 4, '', '', '', '1', '0', 'F', '0', '0', 'system:post:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1025, '岗位导出', 104, 5, '', '', '', '1', '0', 'F', '0', '0', 'system:post:export', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1026, '字典查询', 105, 1, '#', '', '', '1', '0', 'F', '0', '0', 'system:dict:query', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1027, '字典新增', 105, 2, '#', '', '', '1', '0', 'F', '0', '0', 'system:dict:add', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1029, '字典删除', 105, 4, '#', '', '', '1', '0', 'F', '0', '0', 'system:dict:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1030, '字典导出', 105, 5, '#', '', '', '1', '0', 'F', '0', '0', 'system:dict:export', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1031, '参数查询', 106, 1, '#', '', '', '1', '0', 'F', '0', '0', 'system:config:query', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1032, '参数新增', 106, 2, '#', '', '', '1', '0', 'F', '0', '0', 'system:config:add', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1033, '参数修改', 106, 3, '#', '', '', '1', '0', 'F', '0', '0', 'system:config:edit', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1034, '参数删除', 106, 4, '#', '', '', '1', '0', 'F', '0', '0', 'system:config:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1035, '参数导出', 106, 5, '#', '', '', '1', '0', 'F', '0', '0', 'system:config:export', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1036, '公告查询', 107, 1, '#', '', '', '1', '0', 'F', '0', '0', 'system:notice:query', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1037, '公告新增', 107, 2, '#', '', '', '1', '0', 'F', '0', '0', 'system:notice:add', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1038, '公告修改', 107, 3, '#', '', '', '1', '0', 'F', '0', '0', 'system:notice:edit', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1039, '公告删除', 107, 4, '#', '', '', '1', '0', 'F', '0', '0', 'system:notice:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1040, '操作查询', 500, 1, '#', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:query', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1041, '操作删除', 500, 2, '#', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1042, '日志导出', 500, 4, '#', '', '', '1', '0', 'F', '0', '0', 'monitor:operlog:export', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1043, '登录查询', 501, 1, '#', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:query', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (100, '用户管理', 1, 1, '/system/user', '/system/user/index', '', '1', '1', 'C', '0', '0', 'system:user:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-07-10 14:27:23.032', '用户管理菜单', 'true    ');
INSERT INTO "public"."sys_menu" VALUES (1019, '部门修改', 103, 3, '', '', '', '1', '0', 'F', '0', '0', 'system:dept:edit', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1028, '字典修改', 105, 3, '#', '', '', '1', '0', 'F', '0', '0', 'system:dict:edit', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (501, '登录日志', 108, 2, '/monitor/logininfor', '/monitor/logininfor/index', '', '1', '1', 'C', '0', '0', 'monitor:logininfor:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2024-06-28 09:26:46.161', '登录日志菜单', 'true    ');
INSERT INTO "public"."sys_menu" VALUES (1044, '登录删除', 501, 2, '#', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1045, '日志导出', 501, 3, '#', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:export', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1050, '账户解锁', 501, 4, '#', '', '', '1', '0', 'F', '0', '0', 'monitor:logininfor:unlock', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1600, '文件查询', 118, 1, '#', '', '', '1', '0', 'F', '0', '0', 'system:oss:query', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1601, '文件上传', 118, 2, '#', '', '', '1', '0', 'F', '0', '0', 'system:oss:upload', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1602, '文件下载', 118, 3, '#', '', '', '1', '0', 'F', '0', '0', 'system:oss:download', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1603, '文件删除', 118, 4, '#', '', '', '1', '0', 'F', '0', '0', 'system:oss:remove', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1604, '配置添加', 118, 5, '#', '', '', '1', '0', 'F', '0', '0', 'system:oss:add', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (1605, '配置编辑', 118, 6, '#', '', '', '1', '0', 'F', '0', '0', 'system:oss:edit', '', 'admin', '2023-03-15 14:06:16', '', NULL, '', NULL);
INSERT INTO "public"."sys_menu" VALUES (502, '系统信息', 108, 3, '/monitor/systemInfo', '/monitor/systemInfo/index', NULL, '1', '1', 'C', '0', '0', NULL, '', 'admin', '2023-05-10 14:40:58', 'admin', '2023-05-10 14:40:58', '', '1       ');
INSERT INTO "public"."sys_menu" VALUES (500, '操作日志', 108, 1, '/monitor/operlog', '/monitor/operlog/index', '', '1', '1', 'C', '0', '0', 'monitor:operlog:list', '', 'admin', '2023-03-15 14:06:16', 'admin', '2024-06-28 09:26:49.098', '操作日志菜单', 'true    ');
INSERT INTO "public"."sys_menu" VALUES (120, '任务调度中心', 2, 1, 'http://192.168.8.25:7700', '', NULL, '1', '0', 'C', '0', '0', '', '', 'admin', '2023-03-15 14:06:16', 'admin', '2024-06-28 09:27:33.866', 'Powerjob控制台菜单', 'true    ');
INSERT INTO "public"."sys_menu" VALUES (117, 'admin监控', 2, 2, 'http://192.168.8.25:9090/admin', '', NULL, '0', '0', 'C', '0', '0', '', '', 'admin', '2023-03-15 14:06:16', 'admin', '2024-06-28 09:27:38.212', 'admin监控菜单', 'true    ');
INSERT INTO "public"."sys_menu" VALUES (2, '系统监控', 0, 3, 'monitor', NULL, NULL, '1', '0', 'M', '0', '0', NULL, 'monitor', 'admin', '2023-03-15 14:06:16', 'admin', '2024-07-04 14:27:56.335', '系统监控目录', NULL);

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_notice";
CREATE TABLE "public"."sys_notice" (
  "notice_id" int8 NOT NULL,
  "notice_title" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "notice_type" char(1) COLLATE "pg_catalog"."default" NOT NULL,
  "notice_content" bytea,
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(255) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_notice"."notice_id" IS '公告ID';
COMMENT ON COLUMN "public"."sys_notice"."notice_title" IS '公告标题';
COMMENT ON COLUMN "public"."sys_notice"."notice_type" IS '公告类型（1通知 2公告）';
COMMENT ON COLUMN "public"."sys_notice"."notice_content" IS '公告内容';
COMMENT ON COLUMN "public"."sys_notice"."status" IS '公告状态（0正常 1关闭）';
COMMENT ON COLUMN "public"."sys_notice"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_notice"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_notice"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_notice"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_notice"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_notice" IS '通知公告表';

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
INSERT INTO "public"."sys_notice" VALUES (1, '温馨提醒：2018-07-01 新版本发布啦', '2', E'\\346\\226\\260\\347\\211\\210\\346\\234\\254\\345\\206\\205\\345\\256\\271', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '管理员');
INSERT INTO "public"."sys_notice" VALUES (2, '维护通知：2018-07-01 系统凌晨维护', '1', E'\\347\\273\\264\\346\\212\\244\\345\\206\\205\\345\\256\\271', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '管理员');

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_oper_log";
CREATE TABLE "public"."sys_oper_log" (
  "oper_id" int8 NOT NULL,
  "title" varchar(50) COLLATE "pg_catalog"."default",
  "business_type" int4 DEFAULT 0,
  "method" varchar(100) COLLATE "pg_catalog"."default",
  "request_method" varchar(10) COLLATE "pg_catalog"."default",
  "operator_type" int4 DEFAULT 0,
  "oper_name" varchar(50) COLLATE "pg_catalog"."default",
  "dept_name" varchar(50) COLLATE "pg_catalog"."default",
  "oper_url" varchar(255) COLLATE "pg_catalog"."default",
  "oper_ip" varchar(128) COLLATE "pg_catalog"."default",
  "oper_location" varchar(255) COLLATE "pg_catalog"."default",
  "oper_param" varchar(2000) COLLATE "pg_catalog"."default",
  "json_result" varchar(2000) COLLATE "pg_catalog"."default",
  "status" int4 DEFAULT 0,
  "error_msg" varchar(2000) COLLATE "pg_catalog"."default",
  "oper_time" timestamp(6)
)
;
COMMENT ON COLUMN "public"."sys_oper_log"."oper_id" IS '日志主键';
COMMENT ON COLUMN "public"."sys_oper_log"."title" IS '模块标题';
COMMENT ON COLUMN "public"."sys_oper_log"."business_type" IS '业务类型（0其它 1新增 2修改 3删除）';
COMMENT ON COLUMN "public"."sys_oper_log"."method" IS '方法名称';
COMMENT ON COLUMN "public"."sys_oper_log"."request_method" IS '请求方式';
COMMENT ON COLUMN "public"."sys_oper_log"."operator_type" IS '操作类别（0其它 1后台用户 2手机端用户）';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_name" IS '操作人员';
COMMENT ON COLUMN "public"."sys_oper_log"."dept_name" IS '部门名称';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_url" IS '请求URL';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_ip" IS '主机地址';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_location" IS '操作地点';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_param" IS '请求参数';
COMMENT ON COLUMN "public"."sys_oper_log"."json_result" IS '返回参数';
COMMENT ON COLUMN "public"."sys_oper_log"."status" IS '操作状态（0正常 1异常）';
COMMENT ON COLUMN "public"."sys_oper_log"."error_msg" IS '错误消息';
COMMENT ON COLUMN "public"."sys_oper_log"."oper_time" IS '操作时间';
COMMENT ON TABLE "public"."sys_oper_log" IS '操作日志记录';

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_oss
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_oss";
CREATE TABLE "public"."sys_oss" (
  "oss_id" int8 NOT NULL,
  "file_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "original_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "file_suffix" varchar(10) COLLATE "pg_catalog"."default" NOT NULL,
  "url" varchar(500) COLLATE "pg_catalog"."default" NOT NULL,
  "create_time" timestamp(6),
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "service" varchar(20) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'minio'
)
;
COMMENT ON COLUMN "public"."sys_oss"."oss_id" IS '对象存储主键';
COMMENT ON COLUMN "public"."sys_oss"."file_name" IS '文件名';
COMMENT ON COLUMN "public"."sys_oss"."original_name" IS '原名';
COMMENT ON COLUMN "public"."sys_oss"."file_suffix" IS '文件后缀名';
COMMENT ON COLUMN "public"."sys_oss"."url" IS 'URL地址';
COMMENT ON COLUMN "public"."sys_oss"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_oss"."create_by" IS '上传人';
COMMENT ON COLUMN "public"."sys_oss"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_oss"."update_by" IS '更新人';
COMMENT ON COLUMN "public"."sys_oss"."service" IS '服务商';
COMMENT ON TABLE "public"."sys_oss" IS 'OSS对象存储表';

-- ----------------------------
-- Records of sys_oss
-- ----------------------------

-- ----------------------------
-- Table structure for sys_oss_config
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_oss_config";
CREATE TABLE "public"."sys_oss_config" (
  "oss_config_id" int8 NOT NULL,
  "config_key" varchar(20) COLLATE "pg_catalog"."default" NOT NULL,
  "access_key" varchar(255) COLLATE "pg_catalog"."default",
  "secret_key" varchar(255) COLLATE "pg_catalog"."default",
  "bucket_name" varchar(255) COLLATE "pg_catalog"."default",
  "prefix" varchar(255) COLLATE "pg_catalog"."default",
  "endpoint" varchar(255) COLLATE "pg_catalog"."default",
  "domain" varchar(255) COLLATE "pg_catalog"."default",
  "is_https" char(1) COLLATE "pg_catalog"."default" DEFAULT 'N',
  "region" varchar(255) COLLATE "pg_catalog"."default",
  "access_policy" char(1) COLLATE "pg_catalog"."default" NOT NULL DEFAULT '1',
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '1',
  "ext1" varchar(255) COLLATE "pg_catalog"."default",
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_oss_config"."oss_config_id" IS '主建';
COMMENT ON COLUMN "public"."sys_oss_config"."config_key" IS '配置key';
COMMENT ON COLUMN "public"."sys_oss_config"."access_key" IS 'accessKey';
COMMENT ON COLUMN "public"."sys_oss_config"."secret_key" IS '秘钥';
COMMENT ON COLUMN "public"."sys_oss_config"."bucket_name" IS '桶名称';
COMMENT ON COLUMN "public"."sys_oss_config"."prefix" IS '前缀';
COMMENT ON COLUMN "public"."sys_oss_config"."endpoint" IS '访问站点';
COMMENT ON COLUMN "public"."sys_oss_config"."domain" IS '自定义域名';
COMMENT ON COLUMN "public"."sys_oss_config"."is_https" IS '是否https（Y=是,N=否）';
COMMENT ON COLUMN "public"."sys_oss_config"."region" IS '域';
COMMENT ON COLUMN "public"."sys_oss_config"."access_policy" IS '桶权限类型(0=private 1=public 2=custom)';
COMMENT ON COLUMN "public"."sys_oss_config"."status" IS '状态（0=正常,1=停用）';
COMMENT ON COLUMN "public"."sys_oss_config"."ext1" IS '扩展字段';
COMMENT ON COLUMN "public"."sys_oss_config"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_oss_config"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_oss_config"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_oss_config"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_oss_config"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_oss_config" IS '对象存储配置表';

-- ----------------------------
-- Records of sys_oss_config
-- ----------------------------
INSERT INTO "public"."sys_oss_config" VALUES (1, 'minio', 'ruoyi', 'ruoyi123', 'ruoyi', '', '127.0.0.1:9000', '', 'N', '', '1', '0', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-03-15 14:06:16', NULL);
INSERT INTO "public"."sys_oss_config" VALUES (2, 'qiniu', 'XXXXXXXXXXXXXXX', 'XXXXXXXXXXXXXXX', 'ruoyi', '', 's3-cn-north-1.qiniucs.com', '', 'N', '', '1', '1', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-03-15 14:06:16', NULL);
INSERT INTO "public"."sys_oss_config" VALUES (3, 'aliyun', 'XXXXXXXXXXXXXXX', 'XXXXXXXXXXXXXXX', 'ruoyi', '', 'oss-cn-beijing.aliyuncs.com', '', 'N', '', '1', '1', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-03-15 14:06:16', NULL);
INSERT INTO "public"."sys_oss_config" VALUES (4, 'qcloud', 'XXXXXXXXXXXXXXX', 'XXXXXXXXXXXXXXX', 'ruoyi-1250000000', '', 'cos.ap-beijing.myqcloud.com', '', 'N', 'ap-beijing', '1', '1', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-03-15 14:06:16', NULL);
INSERT INTO "public"."sys_oss_config" VALUES (5, 'image', 'ruoyi', 'ruoyi123', 'ruoyi', 'image', '127.0.0.1:9000', '', 'N', '', '1', '1', '', 'admin', '2023-03-15 14:06:16', 'admin', '2023-03-15 14:06:16', NULL);

-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_post";
CREATE TABLE "public"."sys_post" (
  "post_id" int8 NOT NULL,
  "post_code" varchar(64) COLLATE "pg_catalog"."default" NOT NULL,
  "post_name" varchar(50) COLLATE "pg_catalog"."default" NOT NULL,
  "post_sort" int4 NOT NULL,
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL,
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_post"."post_id" IS '岗位ID';
COMMENT ON COLUMN "public"."sys_post"."post_code" IS '岗位编码';
COMMENT ON COLUMN "public"."sys_post"."post_name" IS '岗位名称';
COMMENT ON COLUMN "public"."sys_post"."post_sort" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_post"."status" IS '状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_post"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_post"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_post"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_post"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_post"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_post" IS '岗位信息表';

-- ----------------------------
-- Records of sys_post
-- ----------------------------
INSERT INTO "public"."sys_post" VALUES (1, 'ceo', '董事长', 1, '0', 'admin', '2023-03-15 14:06:16', '', NULL, '');
INSERT INTO "public"."sys_post" VALUES (2, 'se', '项目经理', 2, '0', 'admin', '2023-03-15 14:06:16', '', NULL, '');
INSERT INTO "public"."sys_post" VALUES (3, 'hr', '人力资源', 3, '0', 'admin', '2023-03-15 14:06:16', '', NULL, '');
INSERT INTO "public"."sys_post" VALUES (4, 'user', '普通员工', 4, '0', 'admin', '2023-03-15 14:06:16', 'admin', '2024-06-28 10:24:53.442', '');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role";
CREATE TABLE "public"."sys_role" (
  "role_id" int8 NOT NULL,
  "role_name" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "role_key" varchar(100) COLLATE "pg_catalog"."default" NOT NULL,
  "role_sort" int4 NOT NULL,
  "data_scope" char(1) COLLATE "pg_catalog"."default" DEFAULT '1',
  "menu_check_strictly" char(8) COLLATE "pg_catalog"."default" DEFAULT '1',
  "dept_check_strictly" char(8) COLLATE "pg_catalog"."default" DEFAULT '1',
  "status" char(1) COLLATE "pg_catalog"."default" NOT NULL,
  "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_role"."role_id" IS '角色ID';
COMMENT ON COLUMN "public"."sys_role"."role_name" IS '角色名称';
COMMENT ON COLUMN "public"."sys_role"."role_key" IS '角色权限字符串';
COMMENT ON COLUMN "public"."sys_role"."role_sort" IS '显示顺序';
COMMENT ON COLUMN "public"."sys_role"."data_scope" IS '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）';
COMMENT ON COLUMN "public"."sys_role"."menu_check_strictly" IS '菜单树选择项是否关联显示';
COMMENT ON COLUMN "public"."sys_role"."dept_check_strictly" IS '部门树选择项是否关联显示';
COMMENT ON COLUMN "public"."sys_role"."status" IS '角色状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_role"."del_flag" IS '删除标志（0代表存在 2代表删除）';
COMMENT ON COLUMN "public"."sys_role"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_role"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_role"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_role"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_role"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_role" IS '角色信息表';

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO "public"."sys_role" VALUES (1, '超级管理员', 'admin', 1, '1', '1       ', '1       ', '0', '0', 'admin', '2023-03-15 14:06:16', '', NULL, '超级管理员');
INSERT INTO "public"."sys_role" VALUES (3, '本部门及以下', 'test1', 3, '2', '1       ', '1       ', '0', '0', 'admin', '2023-03-15 14:06:33', 'admin', '2023-03-27 14:17:45', NULL);
INSERT INTO "public"."sys_role" VALUES (2, '普通角色', 'common', 2, '2', 'true    ', 'true    ', '0', '0', 'admin', '2023-03-15 14:06:16', 'admin', '2024-06-22 16:13:00.266', '普通角色');
INSERT INTO "public"."sys_role" VALUES (4, '仅本人', 'test2', 4, '5', 'true    ', 'true    ', '0', '0', 'admin', '2023-03-15 14:06:33', 'admin', '2024-06-22 16:31:03.006', NULL);

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role_dept";
CREATE TABLE "public"."sys_role_dept" (
  "role_id" int8 NOT NULL,
  "dept_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_role_dept"."role_id" IS '角色ID';
COMMENT ON COLUMN "public"."sys_role_dept"."dept_id" IS '部门ID';
COMMENT ON TABLE "public"."sys_role_dept" IS '角色和部门关联表';

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------
INSERT INTO "public"."sys_role_dept" VALUES (3, 100);
INSERT INTO "public"."sys_role_dept" VALUES (3, 101);
INSERT INTO "public"."sys_role_dept" VALUES (3, 103);
INSERT INTO "public"."sys_role_dept" VALUES (3, 104);
INSERT INTO "public"."sys_role_dept" VALUES (3, 105);
INSERT INTO "public"."sys_role_dept" VALUES (3, 106);
INSERT INTO "public"."sys_role_dept" VALUES (2, 100);
INSERT INTO "public"."sys_role_dept" VALUES (2, 101);
INSERT INTO "public"."sys_role_dept" VALUES (2, 103);
INSERT INTO "public"."sys_role_dept" VALUES (2, 105);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_role_menu";
CREATE TABLE "public"."sys_role_menu" (
  "role_id" int8 NOT NULL,
  "menu_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_role_menu"."role_id" IS '角色ID';
COMMENT ON COLUMN "public"."sys_role_menu"."menu_id" IS '菜单ID';
COMMENT ON TABLE "public"."sys_role_menu" IS '角色和菜单关联表';

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO "public"."sys_role_menu" VALUES (3, 1);
INSERT INTO "public"."sys_role_menu" VALUES (3, 100);
INSERT INTO "public"."sys_role_menu" VALUES (3, 101);
INSERT INTO "public"."sys_role_menu" VALUES (3, 102);
INSERT INTO "public"."sys_role_menu" VALUES (3, 103);
INSERT INTO "public"."sys_role_menu" VALUES (3, 104);
INSERT INTO "public"."sys_role_menu" VALUES (3, 105);
INSERT INTO "public"."sys_role_menu" VALUES (3, 106);
INSERT INTO "public"."sys_role_menu" VALUES (3, 107);
INSERT INTO "public"."sys_role_menu" VALUES (3, 108);
INSERT INTO "public"."sys_role_menu" VALUES (3, 500);
INSERT INTO "public"."sys_role_menu" VALUES (3, 501);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1001);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1002);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1003);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1004);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1005);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1006);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1007);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1008);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1009);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1010);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1011);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1012);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1013);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1014);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1015);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1016);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1017);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1018);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1019);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1020);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1021);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1022);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1023);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1024);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1025);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1026);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1027);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1028);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1029);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1030);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1031);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1032);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1033);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1034);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1035);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1036);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1037);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1038);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1039);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1040);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1041);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1042);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1043);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1044);
INSERT INTO "public"."sys_role_menu" VALUES (3, 1045);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1);
INSERT INTO "public"."sys_role_menu" VALUES (2, 100);
INSERT INTO "public"."sys_role_menu" VALUES (2, 108);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1001);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1002);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1003);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1004);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1005);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1006);
INSERT INTO "public"."sys_role_menu" VALUES (2, 101);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1008);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1009);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1010);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1011);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1012);
INSERT INTO "public"."sys_role_menu" VALUES (2, 102);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1013);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1014);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1015);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1016);
INSERT INTO "public"."sys_role_menu" VALUES (2, 103);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1017);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1018);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1019);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1020);
INSERT INTO "public"."sys_role_menu" VALUES (2, 104);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1021);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1022);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1023);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1024);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1025);
INSERT INTO "public"."sys_role_menu" VALUES (2, 105);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1026);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1027);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1028);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1029);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1030);
INSERT INTO "public"."sys_role_menu" VALUES (2, 106);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1031);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1032);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1033);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1034);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1035);
INSERT INTO "public"."sys_role_menu" VALUES (2, 107);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1036);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1037);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1038);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1039);
INSERT INTO "public"."sys_role_menu" VALUES (2, 500);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1040);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1041);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1042);
INSERT INTO "public"."sys_role_menu" VALUES (2, 501);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1043);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1044);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1045);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1050);
INSERT INTO "public"."sys_role_menu" VALUES (2, 118);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1600);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1601);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1602);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1603);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1604);
INSERT INTO "public"."sys_role_menu" VALUES (2, 1605);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1);
INSERT INTO "public"."sys_role_menu" VALUES (4, 100);
INSERT INTO "public"."sys_role_menu" VALUES (4, 101);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1001);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1002);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1003);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1004);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1008);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1009);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1010);
INSERT INTO "public"."sys_role_menu" VALUES (4, 1011);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user";
CREATE TABLE "public"."sys_user" (
  "user_id" int8 NOT NULL,
  "dept_id" int8,
  "user_name" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "nick_name" varchar(30) COLLATE "pg_catalog"."default" NOT NULL,
  "user_type" varchar(10) COLLATE "pg_catalog"."default",
  "email" varchar(50) COLLATE "pg_catalog"."default",
  "phonenumber" varchar(11) COLLATE "pg_catalog"."default",
  "sex" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "avatar" varchar(100) COLLATE "pg_catalog"."default",
  "password" varchar(100) COLLATE "pg_catalog"."default",
  "status" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "del_flag" char(1) COLLATE "pg_catalog"."default" DEFAULT '0',
  "login_ip" varchar(128) COLLATE "pg_catalog"."default",
  "login_date" timestamp(6),
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "create_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "remark" varchar(500) COLLATE "pg_catalog"."default"
)
;
COMMENT ON COLUMN "public"."sys_user"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."sys_user"."dept_id" IS '部门ID';
COMMENT ON COLUMN "public"."sys_user"."user_name" IS '用户账号';
COMMENT ON COLUMN "public"."sys_user"."nick_name" IS '用户昵称';
COMMENT ON COLUMN "public"."sys_user"."user_type" IS '用户类型（sys_user系统用户）';
COMMENT ON COLUMN "public"."sys_user"."email" IS '用户邮箱';
COMMENT ON COLUMN "public"."sys_user"."phonenumber" IS '手机号码';
COMMENT ON COLUMN "public"."sys_user"."sex" IS '用户性别（0男 1女 2未知）';
COMMENT ON COLUMN "public"."sys_user"."avatar" IS '头像地址';
COMMENT ON COLUMN "public"."sys_user"."password" IS '密码';
COMMENT ON COLUMN "public"."sys_user"."status" IS '帐号状态（0正常 1停用）';
COMMENT ON COLUMN "public"."sys_user"."del_flag" IS '删除标志（0代表存在 2代表删除）';
COMMENT ON COLUMN "public"."sys_user"."login_ip" IS '最后登录IP';
COMMENT ON COLUMN "public"."sys_user"."login_date" IS '最后登录时间';
COMMENT ON COLUMN "public"."sys_user"."create_by" IS '创建者';
COMMENT ON COLUMN "public"."sys_user"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."sys_user"."update_by" IS '更新者';
COMMENT ON COLUMN "public"."sys_user"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."sys_user"."remark" IS '备注';
COMMENT ON TABLE "public"."sys_user" IS '用户信息表';

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO "public"."sys_user" VALUES (2, 105, 'lionli', '疯狂的狮子Li', 'sys_user', 'crazyLionLi@qq.com', '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', '2023-03-15 14:06:16', 'admin', '2023-03-15 14:06:16', 'admin', '2023-04-07 14:28:39', '测试员');
INSERT INTO "public"."sys_user" VALUES (4, 102, 'test1', '仅本人 密码666666', 'sys_user', '', '', '0', '', '$2a$10$62Ty/HCnsIr0MxjYM8sDO.JyhQulNZCFb4fqMQO2V2TVzJKd/E1ru', '0', '2', '127.0.0.1', '2023-03-20 09:11:30', 'admin', '2023-03-15 14:06:33', 'admin', '2023-03-30 16:44:58', NULL);
INSERT INTO "public"."sys_user" VALUES (3, 108, 'test', '本部门及以下 密码666666', 'sys_user', '', '', '0', '', '$2a$10$zPvZVqV5xVMVWhI.rUN7mOKplux5DQTofsn3EoXTFCKh05HkEjELy', '0', '0', '192.168.8.252', '2023-04-19 10:17:30', 'admin', '2023-03-15 14:06:33', 'admin', '2024-06-26 17:50:55.028', NULL);
INSERT INTO "public"."sys_user" VALUES (1, 103, 'admin', '疯狂的狮子Li', 'sys_user', 'crazyLionLi@163.com', '15888888888', '0', '', '$2a$10$0gC6a5sEzc4KmtxOomI8ouvUv7sCn2ofgre4eXOaofvL5oWVGA8Re', '0', '0', '192.168.8.42', '2024-07-04 14:27:47.925', 'admin', '2023-03-15 14:06:16', 'admin', '2024-07-04 14:27:47.926', '管理员');

-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user_post";
CREATE TABLE "public"."sys_user_post" (
  "user_id" int8 NOT NULL,
  "post_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_user_post"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."sys_user_post"."post_id" IS '岗位ID';
COMMENT ON TABLE "public"."sys_user_post" IS '用户与岗位关联表';

-- ----------------------------
-- Records of sys_user_post
-- ----------------------------
INSERT INTO "public"."sys_user_post" VALUES (1, 1);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS "public"."sys_user_role";
CREATE TABLE "public"."sys_user_role" (
  "user_id" int8 NOT NULL,
  "role_id" int8 NOT NULL
)
;
COMMENT ON COLUMN "public"."sys_user_role"."user_id" IS '用户ID';
COMMENT ON COLUMN "public"."sys_user_role"."role_id" IS '角色ID';
COMMENT ON TABLE "public"."sys_user_role" IS '用户和角色关联表';

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO "public"."sys_user_role" VALUES (1, 1);
INSERT INTO "public"."sys_user_role" VALUES (2, 2);
INSERT INTO "public"."sys_user_role" VALUES (3, 2);
INSERT INTO "public"."sys_user_role" VALUES (3, 3);

-- ----------------------------
-- Table structure for test_demo
-- ----------------------------
DROP TABLE IF EXISTS "public"."test_demo";
CREATE TABLE "public"."test_demo" (
  "id" int8 NOT NULL,
  "dept_id" int8,
  "user_id" int8,
  "order_num" int4 DEFAULT 0,
  "test_key" varchar(255) COLLATE "pg_catalog"."default",
  "value" varchar(255) COLLATE "pg_catalog"."default",
  "version" int4 DEFAULT 0,
  "create_time" timestamp(6),
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "del_flag" int4 DEFAULT 0
)
;
COMMENT ON COLUMN "public"."test_demo"."id" IS '主键';
COMMENT ON COLUMN "public"."test_demo"."dept_id" IS '部门id';
COMMENT ON COLUMN "public"."test_demo"."user_id" IS '用户id';
COMMENT ON COLUMN "public"."test_demo"."order_num" IS '排序号';
COMMENT ON COLUMN "public"."test_demo"."test_key" IS 'key键';
COMMENT ON COLUMN "public"."test_demo"."value" IS '值';
COMMENT ON COLUMN "public"."test_demo"."version" IS '版本';
COMMENT ON COLUMN "public"."test_demo"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."test_demo"."create_by" IS '创建人';
COMMENT ON COLUMN "public"."test_demo"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."test_demo"."update_by" IS '更新人';
COMMENT ON COLUMN "public"."test_demo"."del_flag" IS '删除标志';
COMMENT ON TABLE "public"."test_demo" IS '测试单表';

-- ----------------------------
-- Records of test_demo
-- ----------------------------
INSERT INTO "public"."test_demo" VALUES (1, 102, 4, 1, '测试数据权限', '测试', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (2, 102, 3, 2, '子节点1', '111', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (3, 102, 3, 3, '子节点2', '222', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (4, 108, 4, 4, '测试数据', 'demo', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (5, 108, 3, 13, '子节点11', '1111', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (6, 108, 3, 12, '子节点22', '2222', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (7, 108, 3, 11, '子节点33', '3333', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (8, 108, 3, 10, '子节点44', '4444', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (9, 108, 3, 9, '子节点55', '5555', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (10, 108, 3, 8, '子节点66', '6666', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (11, 108, 3, 7, '子节点77', '7777', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (12, 108, 3, 6, '子节点88', '8888', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_demo" VALUES (13, 108, 3, 5, '子节点99', '9999', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);

-- ----------------------------
-- Table structure for test_tree
-- ----------------------------
DROP TABLE IF EXISTS "public"."test_tree";
CREATE TABLE "public"."test_tree" (
  "id" int8 NOT NULL,
  "parent_id" int8,
  "dept_id" int8,
  "user_id" int8,
  "tree_name" varchar(255) COLLATE "pg_catalog"."default",
  "version" int4,
  "create_time" timestamp(6),
  "create_by" varchar(64) COLLATE "pg_catalog"."default",
  "update_time" timestamp(6),
  "update_by" varchar(64) COLLATE "pg_catalog"."default",
  "del_flag" int4 DEFAULT 0
)
;
COMMENT ON COLUMN "public"."test_tree"."id" IS '主键';
COMMENT ON COLUMN "public"."test_tree"."parent_id" IS '父id';
COMMENT ON COLUMN "public"."test_tree"."dept_id" IS '部门id';
COMMENT ON COLUMN "public"."test_tree"."user_id" IS '用户id';
COMMENT ON COLUMN "public"."test_tree"."tree_name" IS '值';
COMMENT ON COLUMN "public"."test_tree"."version" IS '版本';
COMMENT ON COLUMN "public"."test_tree"."create_time" IS '创建时间';
COMMENT ON COLUMN "public"."test_tree"."create_by" IS '创建人';
COMMENT ON COLUMN "public"."test_tree"."update_time" IS '更新时间';
COMMENT ON COLUMN "public"."test_tree"."update_by" IS '更新人';
COMMENT ON COLUMN "public"."test_tree"."del_flag" IS '删除标志';
COMMENT ON TABLE "public"."test_tree" IS '测试树表';

-- ----------------------------
-- Records of test_tree
-- ----------------------------
INSERT INTO "public"."test_tree" VALUES (1, 0, 102, 4, '测试数据权限', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (2, 1, 102, 3, '子节点1', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (3, 2, 102, 3, '子节点2', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (4, 0, 108, 4, '测试树1', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (5, 4, 108, 3, '子节点11', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (6, 4, 108, 3, '子节点22', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (7, 4, 108, 3, '子节点33', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (8, 5, 108, 3, '子节点44', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (9, 6, 108, 3, '子节点55', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (10, 7, 108, 3, '子节点66', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (11, 7, 108, 3, '子节点77', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (12, 10, 108, 3, '子节点88', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);
INSERT INTO "public"."test_tree" VALUES (13, 10, 108, 3, '子节点99', 0, '2023-03-15 14:06:33', 'admin', NULL, NULL, 0);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
SELECT setval('"public"."native"', 30, true);

-- ----------------------------
-- Uniques structure for table pj_app_info
-- ----------------------------
ALTER TABLE "public"."pj_app_info" ADD CONSTRAINT "uidx01_app_info" UNIQUE ("app_name");

-- ----------------------------
-- Primary Key structure for table pj_app_info
-- ----------------------------
ALTER TABLE "public"."pj_app_info" ADD CONSTRAINT "pj_app_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_container_info
-- ----------------------------
CREATE INDEX "idx01_container_info" ON "public"."pj_container_info" USING btree (
  "app_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table pj_container_info
-- ----------------------------
ALTER TABLE "public"."pj_container_info" ADD CONSTRAINT "pj_container_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_instance_info
-- ----------------------------
CREATE INDEX "idx01_instance_info" ON "public"."pj_instance_info" USING btree (
  "job_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx02_instance_info" ON "public"."pj_instance_info" USING btree (
  "app_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx03_instance_info" ON "public"."pj_instance_info" USING btree (
  "instance_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table pj_instance_info
-- ----------------------------
ALTER TABLE "public"."pj_instance_info" ADD CONSTRAINT "pj_instance_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_job_info
-- ----------------------------
CREATE INDEX "idx01_job_info" ON "public"."pj_job_info" USING btree (
  "app_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "time_expression_type" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "next_trigger_time" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table pj_job_info
-- ----------------------------
ALTER TABLE "public"."pj_job_info" ADD CONSTRAINT "pj_job_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Uniques structure for table pj_oms_lock
-- ----------------------------
ALTER TABLE "public"."pj_oms_lock" ADD CONSTRAINT "uidx01_oms_lock" UNIQUE ("lock_name");

-- ----------------------------
-- Primary Key structure for table pj_oms_lock
-- ----------------------------
ALTER TABLE "public"."pj_oms_lock" ADD CONSTRAINT "pj_oms_lock_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_server_info
-- ----------------------------
CREATE INDEX "idx01_server_info" ON "public"."pj_server_info" USING btree (
  "gmt_modified" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table pj_server_info
-- ----------------------------
ALTER TABLE "public"."pj_server_info" ADD CONSTRAINT "uidx01_server_info" UNIQUE ("ip");

-- ----------------------------
-- Primary Key structure for table pj_server_info
-- ----------------------------
ALTER TABLE "public"."pj_server_info" ADD CONSTRAINT "pj_server_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_user_info
-- ----------------------------
CREATE INDEX "uidx01_user_info" ON "public"."pj_user_info" USING btree (
  "username" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "uidx02_user_info" ON "public"."pj_user_info" USING btree (
  "email" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table pj_user_info
-- ----------------------------
ALTER TABLE "public"."pj_user_info" ADD CONSTRAINT "pj_user_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_workflow_info
-- ----------------------------
CREATE INDEX "idx01_workflow_info" ON "public"."pj_workflow_info" USING btree (
  "app_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "time_expression_type" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "next_trigger_time" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table pj_workflow_info
-- ----------------------------
ALTER TABLE "public"."pj_workflow_info" ADD CONSTRAINT "pj_workflow_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_workflow_instance_info
-- ----------------------------
CREATE INDEX "idx01_wf_instance" ON "public"."pj_workflow_instance_info" USING btree (
  "workflow_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST,
  "app_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "expected_trigger_time" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Uniques structure for table pj_workflow_instance_info
-- ----------------------------
ALTER TABLE "public"."pj_workflow_instance_info" ADD CONSTRAINT "uidx01_wf_instance" UNIQUE ("wf_instance_id");

-- ----------------------------
-- Primary Key structure for table pj_workflow_instance_info
-- ----------------------------
ALTER TABLE "public"."pj_workflow_instance_info" ADD CONSTRAINT "pj_workflow_instance_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table pj_workflow_node_info
-- ----------------------------
CREATE INDEX "idx01_workflow_node_info" ON "public"."pj_workflow_node_info" USING btree (
  "workflow_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "gmt_create" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table pj_workflow_node_info
-- ----------------------------
ALTER TABLE "public"."pj_workflow_node_info" ADD CONSTRAINT "pj_workflow_node_info_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table sys_config
-- ----------------------------
ALTER TABLE "public"."sys_config" ADD CONSTRAINT "sys_config_pkey" PRIMARY KEY ("config_id");

-- ----------------------------
-- Primary Key structure for table sys_dept
-- ----------------------------
ALTER TABLE "public"."sys_dept" ADD CONSTRAINT "sys_dept_pkey" PRIMARY KEY ("dept_id");

-- ----------------------------
-- Primary Key structure for table sys_dict_data
-- ----------------------------
ALTER TABLE "public"."sys_dict_data" ADD CONSTRAINT "sys_dict_data_pkey" PRIMARY KEY ("dict_code");

-- ----------------------------
-- Indexes structure for table sys_dict_type
-- ----------------------------
CREATE INDEX "dict_type" ON "public"."sys_dict_type" USING btree (
  "dict_type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table sys_dict_type
-- ----------------------------
ALTER TABLE "public"."sys_dict_type" ADD CONSTRAINT "sys_dict_type_pkey" PRIMARY KEY ("dict_id");

-- ----------------------------
-- Primary Key structure for table sys_file
-- ----------------------------
ALTER TABLE "public"."sys_file" ADD CONSTRAINT "sys_file_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table sys_logininfor
-- ----------------------------
CREATE INDEX "idx_sys_logininfor_lt" ON "public"."sys_logininfor" USING btree (
  "login_time" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);
CREATE INDEX "idx_sys_logininfor_s" ON "public"."sys_logininfor" USING btree (
  "status" COLLATE "pg_catalog"."default" "pg_catalog"."bpchar_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table sys_logininfor
-- ----------------------------
ALTER TABLE "public"."sys_logininfor" ADD CONSTRAINT "sys_logininfor_pkey" PRIMARY KEY ("info_id");

-- ----------------------------
-- Primary Key structure for table sys_menu
-- ----------------------------
ALTER TABLE "public"."sys_menu" ADD CONSTRAINT "sys_menu_pkey" PRIMARY KEY ("menu_id");

-- ----------------------------
-- Primary Key structure for table sys_notice
-- ----------------------------
ALTER TABLE "public"."sys_notice" ADD CONSTRAINT "sys_notice_pkey" PRIMARY KEY ("notice_id");

-- ----------------------------
-- Indexes structure for table sys_oper_log
-- ----------------------------
CREATE INDEX "idx_sys_oper_log_bt" ON "public"."sys_oper_log" USING btree (
  "business_type" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "idx_sys_oper_log_ot" ON "public"."sys_oper_log" USING btree (
  "oper_time" "pg_catalog"."timestamp_ops" ASC NULLS LAST
);
CREATE INDEX "idx_sys_oper_log_s" ON "public"."sys_oper_log" USING btree (
  "status" "pg_catalog"."int4_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table sys_oper_log
-- ----------------------------
ALTER TABLE "public"."sys_oper_log" ADD CONSTRAINT "sys_oper_log_pkey" PRIMARY KEY ("oper_id");

-- ----------------------------
-- Primary Key structure for table sys_oss
-- ----------------------------
ALTER TABLE "public"."sys_oss" ADD CONSTRAINT "sys_oss_pkey" PRIMARY KEY ("oss_id");

-- ----------------------------
-- Primary Key structure for table sys_oss_config
-- ----------------------------
ALTER TABLE "public"."sys_oss_config" ADD CONSTRAINT "sys_oss_config_pkey" PRIMARY KEY ("oss_config_id");

-- ----------------------------
-- Primary Key structure for table sys_post
-- ----------------------------
ALTER TABLE "public"."sys_post" ADD CONSTRAINT "sys_post_pkey" PRIMARY KEY ("post_id");

-- ----------------------------
-- Primary Key structure for table sys_role
-- ----------------------------
ALTER TABLE "public"."sys_role" ADD CONSTRAINT "sys_role_pkey" PRIMARY KEY ("role_id");

-- ----------------------------
-- Primary Key structure for table sys_role_dept
-- ----------------------------
ALTER TABLE "public"."sys_role_dept" ADD CONSTRAINT "sys_role_dept_pkey" PRIMARY KEY ("role_id", "dept_id");

-- ----------------------------
-- Primary Key structure for table sys_role_menu
-- ----------------------------
ALTER TABLE "public"."sys_role_menu" ADD CONSTRAINT "sys_role_menu_pkey" PRIMARY KEY ("role_id", "menu_id");

-- ----------------------------
-- Primary Key structure for table sys_user
-- ----------------------------
ALTER TABLE "public"."sys_user" ADD CONSTRAINT "sys_user_pkey" PRIMARY KEY ("user_id");

-- ----------------------------
-- Primary Key structure for table sys_user_post
-- ----------------------------
ALTER TABLE "public"."sys_user_post" ADD CONSTRAINT "sys_user_post_pkey" PRIMARY KEY ("user_id", "post_id");

-- ----------------------------
-- Primary Key structure for table sys_user_role
-- ----------------------------
ALTER TABLE "public"."sys_user_role" ADD CONSTRAINT "sys_user_role_pkey" PRIMARY KEY ("user_id", "role_id");

-- ----------------------------
-- Primary Key structure for table test_demo
-- ----------------------------
ALTER TABLE "public"."test_demo" ADD CONSTRAINT "test_demo_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Primary Key structure for table test_tree
-- ----------------------------
ALTER TABLE "public"."test_tree" ADD CONSTRAINT "test_tree_pkey" PRIMARY KEY ("id");
