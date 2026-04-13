# API 接口设计

## 1. 通用说明

### 1.1 基础信息

| 项目 | 说明 |
|------|------|
| Base URL | `http://localhost:8080/api` |
| 数据格式 | JSON（Content-Type: application/json） |
| 文件上传 | multipart/form-data |
| 流式输出 | text/event-stream（SSE） |

### 1.2 统一响应格式

**成功响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

**失败响应：**
```json
{
  "code": 400,
  "message": "参数错误：项目名称不能为空",
  "data": null
}
```

**分页响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [ ... ],
    "total": 100,
    "current": 1,
    "size": 20
  }
}
```

### 1.3 通用错误码

| 错误码 | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 404 | 资源不存在 |
| 409 | 状态冲突（如非法的状态转移） |
| 500 | 服务器内部错误 |
| 503 | AI 服务不可用 |

---

## 2. 项目管理 API

### 2.1 创建项目

```
POST /api/projects
```

**请求体：**
```json
{
  "projectName": "销售数据仓库",
  "description": "销售部门数仓 ETL 作业开发"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "projectName": "销售数据仓库",
    "description": "销售部门数仓 ETL 作业开发",
    "createdAt": "2025-04-10 10:00:00"
  }
}
```

### 2.2 获取项目列表

```
GET /api/projects
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 按名称模糊搜索 |

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "projectName": "销售数据仓库",
      "description": "销售部门数仓 ETL 作业开发",
      "tableCount": 15,
      "jobCount": 8,
      "createdAt": "2025-04-10 10:00:00"
    }
  ]
}
```

### 2.3 删除项目

```
DELETE /api/projects/{id}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 3. 元数据管理 API

### 3.1 导入 Excel

```
POST /api/projects/{projectId}/metadata/import/excel
Content-Type: multipart/form-data
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | File | 是 | Excel 文件（.xlsx / .xls） |
| schemaName | String | 否 | Schema 名称（如不在 Excel 中） |

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalTables": 10,
    "importedTables": 8,
    "totalColumns": 120,
    "importedColumns": 115,
    "errors": [
      {
        "row": 25,
        "tableName": "order_detail",
        "columnName": "amount",
        "error": "字段类型为空"
      }
    ]
  }
}
```

### 3.2 导入 DDL

```
POST /api/projects/{projectId}/metadata/import/ddl
```

**请求体：**
```json
{
  "ddlContent": "CREATE TABLE order_info (\n  order_id BIGINT PRIMARY KEY,\n  user_id BIGINT COMMENT '用户ID',\n  amount DECIMAL(10,2) COMMENT '金额'\n);\n\nCREATE TABLE user_info (\n  user_id BIGINT PRIMARY KEY,\n  user_name VARCHAR(50) COMMENT '用户名'\n);"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalTables": 2,
    "importedTables": 2,
    "totalColumns": 5,
    "importedColumns": 5,
    "errors": []
  }
}
```

### 3.3 获取表列表

```
GET /api/projects/{projectId}/metadata/tables
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | 否 | 按表名或注释模糊搜索 |

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "tableName": "order_info",
      "tableComment": "订单信息表",
      "schemaName": "sales",
      "sourceType": "EXCEL",
      "columnCount": 12,
      "createdAt": "2025-04-10 10:00:00"
    }
  ]
}
```

### 3.4 获取表详情（含字段）

```
GET /api/projects/{projectId}/metadata/tables/{tableId}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "tableName": "order_info",
    "tableComment": "订单信息表",
    "schemaName": "sales",
    "sourceType": "EXCEL",
    "columns": [
      {
        "id": 1,
        "columnName": "order_id",
        "columnType": "BIGINT",
        "columnComment": "订单ID",
        "isPrimaryKey": true,
        "isNullable": false,
        "defaultValue": null,
        "sortOrder": 1
      },
      {
        "id": 2,
        "columnName": "user_id",
        "columnType": "BIGINT",
        "columnComment": "用户ID",
        "isPrimaryKey": false,
        "isNullable": true,
        "defaultValue": null,
        "sortOrder": 2
      }
    ],
    "createdAt": "2025-04-10 10:00:00"
  }
}
```

### 3.5 更新表信息

```
PUT /api/projects/{projectId}/metadata/tables/{tableId}
```

**请求体：**
```json
{
  "tableComment": "订单信息主表（更新注释）",
  "columns": [
    {
      "id": 1,
      "columnComment": "订单唯一标识ID"
    }
  ]
}
```

### 3.6 删除表

```
DELETE /api/projects/{projectId}/metadata/tables/{tableId}
```

---

## 4. 对话交互 API

### 4.1 创建对话会话

```
POST /api/projects/{projectId}/chat/sessions
```

**请求体：**
```json
{
  "sessionName": "部门销售额统计"
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "projectId": 1,
    "sessionName": "部门销售额统计",
    "currentState": "INIT",
    "availableActions": ["SEND_MESSAGE"],
    "createdAt": "2025-04-10 10:30:00"
  }
}
```

### 4.2 获取对话列表

```
GET /api/projects/{projectId}/chat/sessions
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "sessionName": "部门销售额统计",
      "currentState": "SQL_GENERATION",
      "messageCount": 12,
      "createdAt": "2025-04-10 10:30:00",
      "updatedAt": "2025-04-10 11:00:00"
    }
  ]
}
```

### 4.3 获取对话消息历史

```
GET /api/chat/sessions/{sessionId}/messages
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "role": "user",
      "content": "统计每个部门本月的销售额，按金额从高到低排名",
      "messageType": "TEXT",
      "metadataJson": null,
      "createdAt": "2025-04-10 10:31:00"
    },
    {
      "id": 2,
      "role": "assistant",
      "content": "我已分析您的需求，以下是结构化分析结果：...",
      "messageType": "REQUIREMENT_PARSED",
      "metadataJson": "{\"metrics\":[...],\"dimensions\":[...]}",
      "createdAt": "2025-04-10 10:31:05"
    }
  ]
}
```

### 4.4 发送消息（SSE 流式）

```
POST /api/chat/sessions/{sessionId}/send
Content-Type: application/json
Accept: text/event-stream
```

**请求体：**
```json
{
  "message": "统计每个部门本月的销售额，按金额从高到低排名"
}
```

**SSE 响应流：**
```
event: message
data: 我已

event: message
data: 分析

event: message
data: 您的需求

event: message
data: ...

event: structured
data: {"type":"REQUIREMENT_PARSED","data":{"metrics":[{"name":"销售额","calculation":"SUM"}],"dimensions":[{"name":"部门"}],"filters":[{"description":"本月"}]}}

event: state
data: {"currentState":"REQUIREMENT_ANALYSIS","availableActions":["CONFIRM","REVISE"]}

event: done
data: 
```

### 4.5 确认当前步骤

```
POST /api/chat/sessions/{sessionId}/confirm
```

**请求体（可选，部分步骤可能需要附加数据）：**
```json
{
  "comment": "确认，没有问题"
}
```

**响应（SSE 流式，自动进入下一步并触发 AI）：**
```
event: state
data: {"currentState":"TABLE_RECOMMENDATION","availableActions":["CONFIRM","REVISE","GO_BACK"]}

event: message
data: 根据您的需求，我推荐以下源表...

event: structured
data: {"type":"TABLE_RECOMMEND","data":{"recommended_tables":[...]}}

event: done
data:
```

### 4.6 修改/回退

```
POST /api/chat/sessions/{sessionId}/revise
```

**请求体：**
```json
{
  "action": "REVISE",
  "message": "还需要加上客户维度，按客户和部门双维度统计"
}
```

或回退到上一步：
```json
{
  "action": "GO_BACK",
  "message": "我想重新选择源表"
}
```

**响应：SSE 流式，同 4.4 格式**

### 4.7 删除对话

```
DELETE /api/chat/sessions/{sessionId}
```

---

## 5. 作业管理 API

### 5.1 获取作业列表

```
GET /api/projects/{projectId}/jobs
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | String | 否 | 按状态过滤：DRAFT / CONFIRMED / ARCHIVED |
| keyword | String | 否 | 按名称模糊搜索 |

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "jobName": "部门销售额排名作业",
      "description": "统计每个部门本月销售额并排名",
      "status": "DRAFT",
      "version": 1,
      "stepCount": 3,
      "sessionId": 1,
      "createdAt": "2025-04-10 11:00:00"
    }
  ]
}
```

### 5.2 获取作业详情（含所有步骤）

```
GET /api/jobs/{jobId}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "jobName": "部门销售额排名作业",
    "description": "统计每个部门本月销售额并排名",
    "status": "DRAFT",
    "version": 1,
    "steps": [
      {
        "id": 1,
        "stepOrder": 1,
        "stepName": "关联订单与部门信息",
        "stepType": "CREATE_TEMP",
        "description": "创建临时表，关联 sales_order 和 department，筛选本月数据",
        "ddlSql": "CREATE TABLE tmp_dept_order_monthly (\n  ...\n);",
        "dmlSql": "INSERT INTO tmp_dept_order_monthly\nSELECT ...",
        "sourceTables": "sales_order,department",
        "targetTable": "tmp_dept_order_monthly"
      },
      {
        "id": 2,
        "stepOrder": 2,
        "stepName": "按部门汇总销售额",
        "stepType": "AGGREGATE",
        "description": "按部门分组，计算销售额合计",
        "ddlSql": "CREATE TABLE tmp_dept_sales_summary (\n  ...\n);",
        "dmlSql": "INSERT INTO tmp_dept_sales_summary\nSELECT ...",
        "sourceTables": "tmp_dept_order_monthly",
        "targetTable": "tmp_dept_sales_summary"
      },
      {
        "id": 3,
        "stepOrder": 3,
        "stepName": "生成部门销售排名结果表",
        "stepType": "CREATE_RESULT",
        "description": "按销售额降序排名，写入最终结果表",
        "ddlSql": "CREATE TABLE result_dept_sales_rank (\n  ...\n);",
        "dmlSql": "INSERT INTO result_dept_sales_rank\nSELECT ...",
        "sourceTables": "tmp_dept_sales_summary",
        "targetTable": "result_dept_sales_rank"
      }
    ],
    "createdAt": "2025-04-10 11:00:00",
    "updatedAt": "2025-04-10 11:30:00"
  }
}
```

### 5.3 编辑作业步骤 SQL

```
PUT /api/jobs/{jobId}/steps/{stepId}
```

**请求体：**
```json
{
  "stepName": "关联订单与部门信息（已修改）",
  "ddlSql": "CREATE TABLE tmp_dept_order_monthly (\n  -- 修改后的 DDL\n);",
  "dmlSql": "INSERT INTO tmp_dept_order_monthly\n-- 修改后的 DML\nSELECT ..."
}
```

**响应：**
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 5.4 导出作业

```
GET /api/jobs/{jobId}/export
```

**响应：文件下载（application/zip）**

ZIP 文件结构：
```
部门销售额排名作业/
├── 00_README.txt                          # 作业说明
├── 01_关联订单与部门信息_DDL.sql
├── 01_关联订单与部门信息_DML.sql
├── 02_按部门汇总销售额_DDL.sql
├── 02_按部门汇总销售额_DML.sql
├── 03_生成部门销售排名结果表_DDL.sql
├── 03_生成部门销售排名结果表_DML.sql
└── all_in_one.sql                         # 所有 SQL 合并（按顺序）
```

### 5.5 删除作业

```
DELETE /api/jobs/{jobId}
```

---

## 6. 接口汇总

| 模块 | Method | URL | 说明 |
|------|--------|-----|------|
| 项目 | POST | /api/projects | 创建项目 |
| 项目 | GET | /api/projects | 项目列表 |
| 项目 | DELETE | /api/projects/{id} | 删除项目 |
| 元数据 | POST | /api/projects/{pid}/metadata/import/excel | 导入 Excel |
| 元数据 | POST | /api/projects/{pid}/metadata/import/ddl | 导入 DDL |
| 元数据 | GET | /api/projects/{pid}/metadata/tables | 表列表 |
| 元数据 | GET | /api/projects/{pid}/metadata/tables/{tid} | 表详情 |
| 元数据 | PUT | /api/projects/{pid}/metadata/tables/{tid} | 更新表 |
| 元数据 | DELETE | /api/projects/{pid}/metadata/tables/{tid} | 删除表 |
| 对话 | POST | /api/projects/{pid}/chat/sessions | 创建会话 |
| 对话 | GET | /api/projects/{pid}/chat/sessions | 会话列表 |
| 对话 | GET | /api/chat/sessions/{sid}/messages | 消息历史 |
| 对话 | POST | /api/chat/sessions/{sid}/send | 发送消息(SSE) |
| 对话 | POST | /api/chat/sessions/{sid}/confirm | 确认步骤(SSE) |
| 对话 | POST | /api/chat/sessions/{sid}/revise | 修改/回退(SSE) |
| 对话 | DELETE | /api/chat/sessions/{sid} | 删除会话 |
| 作业 | GET | /api/projects/{pid}/jobs | 作业列表 |
| 作业 | GET | /api/jobs/{jid} | 作业详情 |
| 作业 | PUT | /api/jobs/{jid}/steps/{sid} | 编辑步骤 |
| 作业 | GET | /api/jobs/{jid}/export | 导出作业 |
| 作业 | DELETE | /api/jobs/{jid} | 删除作业 |
