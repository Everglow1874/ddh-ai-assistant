## Context

### 当前状态
- 已完成 Phase 0-2：环境搭建、元数据管理、AI 对话生成 SQL
- 已有数据库表：`etl_job`、`etl_job_step`
- 已有后端基础框架：Spring Boot + MyBatis-Plus

### 需求背景
用户通过 AI 对话生成 SQL 方案后，需要：
1. 将方案持久化保存为作业
2. 支持作业列表查看和管理
3. 支持在线编辑和修改 SQL
4. 导出为可执行的 SQL 文件包

### 约束
- 前端使用 React + TypeScript + Ant Design
- SQL 编辑使用 Monaco Editor
- 导出格式为 ZIP（包含 DDL/DML 文件）

## Goals / Non-Goals

**Goals:**
- 实现作业的完整生命周期管理（创建、编辑、删除、导出）
- 提供 Monaco Editor 集成，支持 SQL 在线编辑
- 实现工作台右侧 SQL 预览和编辑功能
- 支持导出为 ZIP 文件包

**Non-Goals:**
- 不实现作业调度执行功能（仅导出 SQL 文件）
- 不实现多版本管理（后续迭代）
- 不实现作业模板库（后续迭代）

## Decisions

### 1. 前端状态管理方案
**决定**: 使用 Zustand 管理作业状态

**理由**: 项目已使用 Zustand，保持统一；Zustand 轻量且适合中型状态管理

### 2. Monaco Editor 集成
**决定**: 使用 `@monaco-editor/react` 封装组件

**理由**: React 最成熟的 Monaco Editor 封装，支持 TypeScript，配置简单

### 3. ZIP 导出方案
**决定**: 后端生成 ZIP 文件流，前端下载

**理由**: 
- 后端处理文件打包更灵活
- 减少前端依赖
- Java `java.util.zip` 内置支持

### 4. SQL 存储格式
**决定**: DDL 和 DML 分开存储

**理由**:
- 清晰区分建表和数据处理逻辑
- 方便用户理解和修改
- 符合实际 ETL 作业习惯

## Risks / Trade-offs

- **[风险]**: 大型 SQL 文件可能超出内存限制
  - **缓解**: 流式处理，分块写入 ZIP
  
- **[风险]**: Monaco Editor 在低配机器上可能卡顿
  - **缓解**: 按需加载，仅在编辑时加载完整编辑器

- **[权衡]**: 作业编辑功能 vs 复杂度
  - **选择**: 仅支持单步 SQL 编辑，不支持批量编辑

## Migration Plan

1. 后端：新增 `JobService`、`JobController`、`JobExportService`
2. 前端：新增 Monaco Editor 组件、完善工作台、作业列表页
3. 前端：路由配置更新
4. 联调测试

## Open Questions

- [ ] 是否需要支持作业复制功能？
- [ ] 导出文件结构是否满足实际需求？
