## Why

目前系统已实现元数据管理和 AI 对话生成 SQL 的核心流程，但缺少作业的持久化管理能力。用户通过 AI 对话生成的 SQL 方案无法保存、编辑和导出，导致每次都需要重新对话生成。需要快速建立作业的完整生命周期管理。

## What Changes

### 后端
1. 新增作业保存功能：对话完成后自动将 SQL 方案保存为 ETL 作业
2. 新增作业 CRUD API：作业列表、详情、编辑
3. 新增作业导出功能：生成 ZIP 文件包（包含 DDL/DML SQL 文件）

### 前端
1. 新增 Monaco Editor 组件：SQL 语法高亮、编辑功能
2. 完善 SQL 预览面板：工作台右侧展示各步骤 SQL
3. 新增作业列表页：作业列表、搜索、过滤
4. 新增作业详情页：步骤流程展示、SQL 编辑
5. 新增导出功能：下载 ZIP 文件

## Capabilities

### New Capabilities
- `job-management`: 作业的创建、编辑、删除、列表查询功能
- `job-export`: 将作业导出为 SQL 文件包（ZIP 格式）
- `sql-editor`: Monaco Editor 集成的 SQL 在线编辑能力
- `job-detail-view`: 作业详情查看和步骤流程展示

### Modified Capabilities
- （无）

## Impact

- **后端模块**: 新增 `JobService`、`JobController`
- **前端模块**: 完善 `WorkbenchPage`、`JobListPage`、`JobDetailPage`
- **数据库**: 使用已有的 `etl_job`、`etl_job_step` 表
- **依赖**: 前端新增 `@monaco-editor/react`
