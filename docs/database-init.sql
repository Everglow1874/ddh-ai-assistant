-- ============================================================
-- DDH-Assistant 数据库初始化脚本
-- 数据库: MySQL 8.0
-- 字符集: utf8mb4
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS ddh_assistant
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE ddh_assistant;

-- ============================================================
-- 1. 项目表
-- ============================================================
DROP TABLE IF EXISTS `project`;
CREATE TABLE `project` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_name`  VARCHAR(100) NOT NULL COMMENT '项目名称',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '项目描述',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='项目表';

-- ============================================================
-- 2. 表元数据
-- ============================================================
DROP TABLE IF EXISTS `table_metadata`;
CREATE TABLE `table_metadata` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`    BIGINT       NOT NULL COMMENT '所属项目ID',
    `table_name`    VARCHAR(200) NOT NULL COMMENT '表名',
    `table_comment` VARCHAR(500) DEFAULT NULL COMMENT '表注释',
    `schema_name`   VARCHAR(100) DEFAULT NULL COMMENT 'Schema名称',
    `source_type`   VARCHAR(20)  NOT NULL DEFAULT 'EXCEL' COMMENT '来源类型: EXCEL/DDL',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_project_id` (`project_id`),
    UNIQUE KEY `uk_project_table` (`project_id`, `schema_name`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='表元数据';

-- ============================================================
-- 3. 字段元数据
-- ============================================================
DROP TABLE IF EXISTS `column_metadata`;
CREATE TABLE `column_metadata` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `table_id`       BIGINT       NOT NULL COMMENT '所属表ID',
    `column_name`    VARCHAR(200) NOT NULL COMMENT '字段名',
    `column_type`    VARCHAR(100) NOT NULL COMMENT '字段类型',
    `column_comment` VARCHAR(500) DEFAULT NULL COMMENT '字段注释',
    `is_primary_key` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否主键: 0-否 1-是',
    `is_nullable`    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可空: 0-否 1-是',
    `default_value`  VARCHAR(200) DEFAULT NULL COMMENT '默认值',
    `sort_order`     INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    PRIMARY KEY (`id`),
    INDEX `idx_table_id` (`table_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='字段元数据';

-- ============================================================
-- 4. 对话会话
-- ============================================================
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`     BIGINT       NOT NULL COMMENT '所属项目ID',
    `session_name`   VARCHAR(200) DEFAULT NULL COMMENT '会话名称',
    `current_state`  VARCHAR(50)  NOT NULL DEFAULT 'INIT' COMMENT '当前对话状态: INIT/REQUIREMENT_ANALYSIS/TABLE_RECOMMENDATION/STEP_DESIGN/SQL_GENERATION/SQL_REVIEW/JOB_ASSEMBLY/DONE',
    `context_json`   TEXT         DEFAULT NULL COMMENT '对话上下文(JSON格式)',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对话会话';

-- ============================================================
-- 5. 对话消息
-- ============================================================
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id`     BIGINT       NOT NULL COMMENT '所属会话ID',
    `role`           VARCHAR(20)  NOT NULL COMMENT '消息角色: user/assistant/system',
    `content`        TEXT         NOT NULL COMMENT '消息内容',
    `message_type`   VARCHAR(30)  NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT/REQUIREMENT_PARSED/TABLE_RECOMMEND/STEP_DESIGN/SQL_DDL/SQL_DML/SQL_REVIEW/JOB_SUMMARY',
    `metadata_json`  TEXT         DEFAULT NULL COMMENT '附加结构化数据(JSON格式)',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='对话消息';

-- ============================================================
-- 6. ETL 作业
-- ============================================================
DROP TABLE IF EXISTS `etl_job`;
CREATE TABLE `etl_job` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `project_id`     BIGINT       NOT NULL COMMENT '所属项目ID',
    `session_id`     BIGINT       DEFAULT NULL COMMENT '来源对话ID',
    `job_name`       VARCHAR(200) NOT NULL COMMENT '作业名称',
    `description`    VARCHAR(500) DEFAULT NULL COMMENT '作业描述',
    `status`         VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/CONFIRMED/ARCHIVED',
    `version`        INT          NOT NULL DEFAULT 1 COMMENT '版本号',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_project_id` (`project_id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='ETL作业';

-- ============================================================
-- 7. 作业步骤
-- ============================================================
DROP TABLE IF EXISTS `etl_job_step`;
CREATE TABLE `etl_job_step` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `job_id`         BIGINT       NOT NULL COMMENT '所属作业ID',
    `step_order`     INT          NOT NULL COMMENT '步骤顺序(从1开始)',
    `step_name`      VARCHAR(200) NOT NULL COMMENT '步骤名称',
    `step_type`      VARCHAR(30)  NOT NULL COMMENT '步骤类型: CREATE_TEMP/TRANSFORM/AGGREGATE/CREATE_RESULT',
    `description`    VARCHAR(500) DEFAULT NULL COMMENT '步骤描述',
    `ddl_sql`        TEXT         DEFAULT NULL COMMENT '建表DDL语句',
    `dml_sql`        TEXT         DEFAULT NULL COMMENT '数据处理DML语句',
    `source_tables`  VARCHAR(500) DEFAULT NULL COMMENT '依赖的源表(逗号分隔)',
    `target_table`   VARCHAR(200) DEFAULT NULL COMMENT '目标输出表名',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_job_id` (`job_id`),
    UNIQUE KEY `uk_job_step` (`job_id`, `step_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作业步骤';

-- ============================================================
-- 初始化数据（可选）
-- ============================================================

-- 插入一个示例项目
INSERT INTO `project` (`project_name`, `description`)
VALUES ('示例项目', '这是一个示例项目，用于演示系统功能。可在熟悉系统后删除。');
