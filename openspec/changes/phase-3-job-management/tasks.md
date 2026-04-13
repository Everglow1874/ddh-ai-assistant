## 1. 后端 - 作业管理

- [x] 1.1 创建 JobService（作业 CRUD）
- [x] 1.2 创建 JobController（REST API）
- [x] 1.3 实现作业保存功能（从对话保存为作业）
- [x] 1.4 实现作业导出功能（生成 ZIP）

## 2. 后端 - 作业管理 API

- [x] 2.1 GET /api/projects/{projectId}/jobs - 作业列表
- [x] 2.2 GET /api/jobs/{jobId} - 作业详情（含步骤）
- [x] 2.3 PUT /api/jobs/{jobId}/steps/{stepId} - 编辑步骤 SQL
- [x] 2.4 GET /api/jobs/{jobId}/export - 导出作业
- [x] 2.5 DELETE /api/jobs/{jobId} - 删除作业

## 3. 前端 - Monaco Editor 组件

- [x] 3.1 安装 @monaco-editor/react
- [x] 3.2 创建 SqlEditor 组件（封装 Monaco Editor）
- [x] 3.3 创建 SqlPreview 组件（只读模式）

## 4. 前端 - 工作台完善

- [x] 4.1 右侧 SQL 预览面板完善
- [x] 4.2 作业保存入口（保存为作业按钮）
- [x] 4.3 作业列表入口（跳转链接）

## 5. 前端 - 作业列表页

- [x] 5.1 创建 JobListPage 路由
- [x] 5.2 作业列表展示（表格）
- [x] 5.3 搜索/过滤功能
- [x] 5.4 删除作业功能

## 6. 前端 - 作业详情页

- [x] 6.1 创建 JobDetailPage 路由
- [x] 6.2 步骤流程可视化
- [x] 6.3 步骤 SQL 展示
- [x] 6.4 Monaco Editor 集成（编辑 SQL）
- [x] 6.5 导出按钮

## 7. 联调与测试

- [x] 7.1 前后端联调
- [x] 7.2 测试导出功能
- [x] 7.3 Bug 修复
