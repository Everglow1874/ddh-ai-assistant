# DDH-Assistant 项目指南

> 本文件面向 AI 编程助手。项目的主要自然语言为 **中文**（UI 文案、文档、注释均使用中文）。

---

## 1. 项目概述

**DDH-Assistant（数仓开发助手）** 是一个基于 AI 多轮对话的数据仓库 ETL 作业流开发平台。

核心工作流：
1. 用户导入源表元数据（Excel / DDL）。
2. 通过自然语言描述统计需求。
3. AI 分步引导完成：需求分析 → 源表推荐 → 步骤拆分 → SQL 生成 → SQL 审查。
4. 最终产出可在 GaussDB 计算集群上执行的完整 SQL 作业脚本，支持导出为 ZIP 文件包。

本项目为本地开发辅助工具，**无用户认证/授权层**。

---

## 2. 技术栈

| 层面 | 技术 | 版本/说明 |
|------|------|----------|
| 后端语言 | Java | 8 |
| 后端框架 | Spring Boot | 2.7.18 |
| ORM | MyBatis-Plus | 3.5.5 |
| 构建工具 | Maven | 3.x |
| 数据库 | MySQL | 8.0 |
| 前端框架 | React + TypeScript | 19.x |
| UI 组件库 | Ant Design | 6.x |
| 前端构建 | Vite | 8.x |
| SQL 编辑器 | Monaco Editor (@monaco-editor/react) | - |
| Markdown 渲染 | react-markdown + remark-gfm | - |
| HTTP 客户端 | Axios（前端）/ OkHttp（后端） | - |
| AI 接口 | OpenAI 兼容格式（SSE 流式） | 当前配置：DashScope / deepseek-v3.2 |
| Excel 解析 | EasyExcel | 3.3.3 |

---

## 3. 项目结构

```
ddh-assistant/
├── docs/                          # 设计文档（中文 Markdown）
│   ├── 01-项目概述.md
│   ├── 02-数据库设计.md
│   ├── 03-后端设计.md
│   ├── 04-API接口设计.md
│   ├── 05-前端设计.md
│   ├── 06-AI-Prompt设计.md
│   ├── 07-开发计划.md
│   ├── 08-AI辅助数仓开发流程.md
│   └── database-init.sql          # 数据库初始化脚本
│
├── ddh-backend/                   # 后端 Spring Boot 项目
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/ddh/assistant/
│       │   ├── DdhAssistantApplication.java
│       │   ├── config/            # 配置类（CORS、MyBatis-Plus、LLM）
│       │   ├── common/            # 统一响应体 Result、全局异常处理
│       │   ├── controller/        # REST API 控制器
│       │   ├── service/           # 业务逻辑层
│       │   │   ├── ai/            # LLM 调用、Prompt 构建
│       │   │   ├── chat/          # 对话管理（状态机核心）
│       │   │   ├── job/           # 作业管理、导出
│       │   │   └── metadata/      # 元数据、Excel/DDL 解析
│       │   ├── model/entity/      # 数据库实体（7 张表）
│       │   └── mapper/            # MyBatis-Plus Mapper 接口
│       └── main/resources/
│           ├── application.yml    # 主配置
│           ├── application-dev.yml# 开发环境配置（含 LLM API Key）
│           └── prompts/           # AI Prompt 模板（.txt）
│
├── ddh-frontend/                  # 前端 React 项目
│   ├── package.json
│   ├── vite.config.ts             # Vite 配置（含 /api -> :8080 代理）
│   └── src/
│       ├── main.tsx               # 入口
│       ├── App.tsx                # 路由定义
│       ├── api/                   # API 模块（Axios + SSE fetch）
│       ├── components/            # 复用组件（Layout、MarkdownBubble、SqlEditor）
│       ├── pages/                 # 页面级组件
│       │   ├── ProjectListPage/   # 项目列表（首页）
│       │   ├── MetadataPage/      # 元数据管理
│       │   ├── WorkbenchPage/     # AI 开发工作台（核心页面，最复杂）
│       │   ├── JobListPage/       # 作业列表
│       │   └── JobDetailPage/     # 作业步骤编辑
│       └── styles/global.css
│
├── openspec/                      # OpenSpec 变更管理
│   ├── changes/
│   ├── specs/
│   └── config.yaml
│
├── .opencode/                     # OpenCode AI 工作流工具
│   ├── command/                   # 自定义命令 Markdown
│   └── skills/                    # OpenSpec 相关 Skills
│
└── py/demo.py                     # Python 脚本：DashScope API 调用示例
```

---

## 4. 构建与运行命令

### 后端（ddh-backend）

```bash
# 编译 & 打包
cd ddh-backend
mvn clean package

# 运行（需要 MySQL 已启动，数据库已初始化）
mvn spring-boot:run
# 或
java -jar target/ddh-assistant-1.0.0-SNAPSHOT.jar
```

- 默认端口：**8080**
- 开发环境激活 `spring.profiles.active=dev`，使用 `application-dev.yml`
- 数据库需预先执行 `docs/database-init.sql`

### 前端（ddh-frontend）

```bash
cd ddh-frontend
npm install
npm run dev      # 开发服务器，端口 3000，已代理 /api 到 localhost:8080
npm run build    # 生产构建（tsc -b && vite build）
npm run lint     # ESLint 检查
```

- Vite 开发服务器已配置 proxy：`/api` → `http://localhost:8080`
- 生产构建产物输出到 `ddh-frontend/dist/`

---

## 5. 代码组织与约定

### 后端约定

- **分层架构**：Controller → Service → Mapper/Entity。无 XML Mapper，全部使用 MyBatis-Plus `BaseMapper` + `LambdaQueryWrapper`。
- **无 DTO/VO 层**：Controller 大多直接返回 Entity（除 `JobController` 中的少量 VO 外）。设计文档中描述的 DTO、VO、Agent 基类等在实际代码中**并未实现**。
- **统一响应**：所有接口返回 `Result<T>` 包装体，`code=200` 为成功。
- **异常处理**：`GlobalExceptionHandler` 统一处理，但会跳过 SSE 请求（避免干扰 `SseEmitter`）。
- **事务边界**：`ChatService.processMessageStream()` 不标注 `@Transactional`（流式 IO 跨越事务边界），但独立的消息保存方法有事务保护。
- **Lombok**：广泛使用 `@Data`、`@Slf4j` 等注解。

### 前端约定

- **页面级组件**：`pages/PageName/index.tsx`，每个页面为一个独立文件，通常较大（如 `WorkbenchPage` 约 530 行）。
- **复用组件**：`components/ComponentName/ComponentName.tsx` + `index.ts` barrel export。
- **无全局状态管理**：没有使用 Zustand、Redux 或 Context API。所有状态通过 React `useState`/`useEffect` 在页面内本地管理。
- **API 层**：`api/request.ts` 提供 Axios 实例，响应拦截器自动解包 `Result<T>` 并在 `code !== 200` 时弹错误提示。SSE 流使用原生 `fetch` 实现。
- **样式**：几乎全部为内联 `style={{ ... }}`，极少使用外部 CSS。

### 多语言与文案

- 界面文案、文档、注释、Prompt 模板、数据库表注释均使用**中文**。
- API 接口路径和代码变量名使用英文。

---

## 6. 数据库

- **数据库名**：`ddh_assistant`
- **字符集**：`utf8mb4`
- **初始化脚本**：`docs/database-init.sql`

### 核心表

| 表名 | 说明 |
|------|------|
| `project` | 项目 |
| `table_metadata` | 源表元数据 |
| `column_metadata` | 字段元数据 |
| `chat_session` | 对话会话（含 `current_state`、`context_json`） |
| `chat_message` | 对话消息 |
| `etl_job` | ETL 作业 |
| `etl_job_step` | 作业步骤（含 `ddl_sql`、`dml_sql`） |

### 对话状态机

`chat_session.current_state` 的流转：

```
INIT → REQUIREMENT_ANALYSIS → TABLE_RECOMMENDATION → STEP_DESIGN → SQL_GENERATION → SQL_REVIEW → DONE
```

`context_json` 以 JSON 文本存储中间结果（需求解析、推荐表、步骤设计、SQL 等）。

---

## 7. AI 集成

- **协议**：OpenAI 兼容的 Chat Completions API。
- **传输**：SSE（Server-Sent Events）流式输出，前端实时展示 token。
- **后端实现**：`LlmService.chatStream()` 使用 OkHttp SSE 读取，逐 token 回调推送至 `SseEmitter`。
- **Prompt 模板**：位于 `ddh-backend/src/main/resources/prompts/`，共 5 个阶段：
  - `requirement-analysis.txt`
  - `table-recommend.txt`
  - `step-design.txt`
  - `sql-generate.txt`
  - `sql-review.txt`
- **当前配置**（`application-dev.yml`）：
  - `base-url`: `https://dashscope.aliyuncs.com/compatible-mode/v1`
  - `model`: `deepseek-v3.2`
  - `timeout`: 120s

---

## 8. 测试

- **后端**：`src/test/java/` 目录存在，但**当前无任何测试代码**。
- **前端**：无测试框架配置。

如需添加测试，后端推荐继续使用 `spring-boot-starter-test`（已在 `pom.xml` 中引入），前端可引入 Vitest 或 Jest。

---

## 9. 安全注意事项

- **无认证授权**：本项目为本地开发工具，所有接口公开可访问。
- **硬编码 API Key**：`application-dev.yml` 中 `llm.api-key` 为明文硬编码。请勿提交到公共仓库；生产环境应使用环境变量或密钥管理服务。
- **文件上传限制**：`multipart.max-file-size=10MB`，元数据导入接口接收 Excel/DDL 文件。
- **CORS**：`WebMvcConfig` 已配置允许跨域，支持前端开发服务器直连。

---

## 10. 设计文档与实际代码的差异

`docs/` 目录中的设计文档（尤其是 `03-后端设计.md`）描述了一个比当前代码更复杂的架构，包含：

- Agent 基类与具体 Agent 实现（`BaseAgent`、`RequirementAgent` 等）—— **未实现**
- 独立的 `ChatContextManager`、`ConversationStateMachine` 类 —— **未实现**，状态机逻辑内聚在 `ChatService` 中
- 完整的 DTO/VO/枚举分层 —— **大部分未实现**
- 前端使用 Zustand 状态管理 —— **未使用**
- 文档标注的技术版本（React 18、Ant Design 5、Vite 5）—— 实际为 **React 19、Ant Design 6、Vite 8**

> **代码为准**。修改代码时，请以 `ddh-backend/src/` 和 `ddh-frontend/src/` 中的实际实现为准，而非设计文档中的架构图。

---

## 11. OpenSpec / OpenCode 工作流

本项目使用了 `.opencode/` 工具链进行 **Spec-Driven Development**（规格驱动开发）：

- `openspec/config.yaml`：配置规格驱动模式。
- `openspec/changes/` 与 `openspec/specs/`：存放变更规格与规格文档。
- `.opencode/skills/`：定义了多个 OpenSpec 相关技能（如 `openspec-new-change`、`openspec-apply-change`、`openspec-verify-change` 等）。

如果你需要以规格驱动方式新增功能，请参考 `.opencode/skills/` 中的 SKILL.md 文件。
