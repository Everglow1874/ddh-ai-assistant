package com.ddh.assistant.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddh.assistant.mapper.ChatSessionMapper;
import com.ddh.assistant.mapper.ChatMessageMapper;
import com.ddh.assistant.model.entity.ChatSession;
import com.ddh.assistant.model.entity.ChatMessage;
import com.ddh.assistant.model.entity.TableMetadata;
import com.ddh.assistant.model.entity.ColumnMetadata;
import com.ddh.assistant.service.ai.LlmService;
import com.ddh.assistant.service.ai.PromptBuilder;
import com.ddh.assistant.service.metadata.MetadataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对话服务（多阶段 Agent，上下文通过 contextJson 持久化）
 *
 * 状态机：
 *   INIT → TABLE_RECOMMENDATION → STEP_DESIGN → SQL_GENERATION → SQL_REVIEW → DONE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final MetadataService metadataService;
    private final LlmService llmService;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 会话管理 ==========

    public ChatSession createSession(Long projectId, String sessionName) {
        ChatSession session = new ChatSession();
        session.setProjectId(projectId);
        session.setSessionName(sessionName != null ? sessionName : "新会话");
        session.setCurrentState("INIT");
        session.setContextJson("{}");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    public Page<ChatSession> listSessions(Long projectId, int current, int size) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getProjectId, projectId);
        wrapper.orderByDesc(ChatSession::getCreatedAt);
        return sessionMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public ChatSession getSession(Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("会话不存在: " + sessionId);
        }
        return session;
    }

    public List<ChatMessage> listMessages(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        wrapper.orderByAsc(ChatMessage::getCreatedAt);
        return messageMapper.selectList(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId);
        messageMapper.delete(wrapper);
        sessionMapper.deleteById(sessionId);
    }

    // ========== 消息处理 ==========

    /**
     * 流式处理消息，onToken 回调每个 token 片段
     * 注意：不能加 @Transactional，因为流式 IO 会跨事务边界
     */
    public String processMessageStream(Long sessionId, String userMessage,
                                        Consumer<String> onToken) throws Exception {
        ChatSession session = getSession(sessionId);
        String currentState = session.getCurrentState();

        // 保存用户消息（独立事务）
        saveMessageTx(sessionId, "user", userMessage, "TEXT");

        // 解析当前上下文
        ObjectNode context = parseContext(session.getContextJson());

        String responseContent;
        String nextState;

        switch (currentState) {
            case "INIT":
                responseContent = processRequirementAnalysis(session, userMessage, context, onToken);
                context.put("userRequirement", userMessage);
                String reqContextJson = extractJsonContext(responseContent);
                context.put("requirementAnalysis", reqContextJson != null ? reqContextJson : responseContent);
                // 提取需求摘要并自动重命名会话
                if (reqContextJson != null) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode reqNode = objectMapper.readTree(reqContextJson);
                        if (reqNode.has("summary")) {
                            String summary = reqNode.get("summary").asText();
                            if (summary != null && !summary.isEmpty()) {
                                session.setSessionName(summary.length() > 50 ? summary.substring(0, 50) + "…" : summary);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                log.info("[Chat] INIT 完成, jsonContext提取={}, 响应长度={}", reqContextJson != null, responseContent.length());
                nextState = "TABLE_RECOMMENDATION";
                break;

            case "TABLE_RECOMMENDATION":
                responseContent = processTableRecommend(session, userMessage, context, onToken);
                context.put("tableConfirmation", userMessage);
                String tableContextJson = extractJsonContext(responseContent);
                context.put("tableRecommendation", tableContextJson != null ? tableContextJson : responseContent);
                log.info("[Chat] TABLE_RECOMMENDATION 完成, jsonContext提取={}", tableContextJson != null);
                nextState = "STEP_DESIGN";
                break;

            case "STEP_DESIGN":
                responseContent = processStepDesign(session, userMessage, context, onToken);
                String stepContextJson = extractJsonContext(responseContent);
                context.put("stepDesign", stepContextJson != null ? stepContextJson : responseContent);
                log.info("[Chat] STEP_DESIGN 完成, jsonContext提取={}", stepContextJson != null);
                nextState = "SQL_GENERATION";
                break;

            case "SQL_GENERATION":
                responseContent = processSqlGenerate(session, userMessage, context, onToken);
                String extractedSql = extractSqlBlocks(responseContent);
                context.put("generatedSql", extractedSql != null ? extractedSql : responseContent);
                String sqlContextJson = extractJsonContext(responseContent);
                if (sqlContextJson != null) {
                    context.put("sqlContext", sqlContextJson);
                }
                log.info("[Chat] SQL_GENERATION 完成, SQL提取={}, SQL长度={}, jsonContext提取={}",
                        extractedSql != null,
                        extractedSql != null ? extractedSql.length() : 0,
                        sqlContextJson != null);
                if (extractedSql != null) {
                    log.info("[Chat] 提取的SQL前100字符: {}", extractedSql.substring(0, Math.min(100, extractedSql.length())));
                }
                nextState = "SQL_REVIEW";
                break;

            case "SQL_REVIEW":
                responseContent = processSqlReview(session, userMessage, context, onToken);
                String reviewContextJson = extractJsonContext(responseContent);
                context.put("reviewResult", reviewContextJson != null ? reviewContextJson : responseContent);
                log.info("[Chat] SQL_REVIEW 完成, jsonContext提取={}", reviewContextJson != null);
                nextState = "DONE";
                break;

            case "DONE":
                responseContent = processFreeChat(session, userMessage, context, onToken);
                String freeSql = extractSqlBlocks(responseContent);
                if (freeSql != null) {
                    context.put("generatedSql", freeSql);
                    log.info("[Chat] DONE 阶段发现新SQL, 已更新 generatedSql");
                }
                nextState = "DONE";
                break;

            default:
                responseContent = "会话状态异常，请重新开始。";
                nextState = "INIT";
                if (onToken != null) onToken.accept(responseContent);
        }

        // 保存 AI 响应（独立事务）
        saveMessageTx(sessionId, "assistant", responseContent, "TEXT");

        // 更新会话状态和上下文
        session.setCurrentState(nextState);
        log.info("[Chat] 状态转换: {} -> {}, context keys: {}", currentState, nextState, context.fieldNames());
        session.setContextJson(objectMapper.writeValueAsString(context));
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(session);

        return responseContent;
    }

    // ========== 各阶段处理（带 onToken 回调）==========

    private String processRequirementAnalysis(ChatSession session, String userMessage,
                                               ObjectNode context, Consumer<String> onToken) throws Exception {
        List<TableMetadata> tables = metadataService.listAllTables(session.getProjectId());
        String prompt = promptBuilder.buildRequirementPrompt(userMessage, tables);

        List<LlmService.Message> messages = new ArrayList<>();
        messages.add(new LlmService.Message("user", userMessage));

        log.debug("需求分析 - 调用 LLM, 表数量: {}", tables.size());
        return llmService.chatStream(prompt, messages, onToken);
    }

    private String processTableRecommend(ChatSession session, String userMessage,
                                          ObjectNode context, Consumer<String> onToken) throws Exception {
        List<TableMetadata> tables = metadataService.listAllTables(session.getProjectId());
        List<ColumnMetadata> allColumns = new ArrayList<>();
        for (TableMetadata table : tables) {
            allColumns.addAll(metadataService.listColumns(table.getId()));
        }

        String requirementJson = context.has("requirementAnalysis")
                ? context.get("requirementAnalysis").asText()
                : context.has("userRequirement") ? context.get("userRequirement").asText() : "{}";

        String prompt = promptBuilder.buildTableRecommendPrompt(requirementJson, tables, allColumns);

        List<LlmService.Message> messages = new ArrayList<>();
        if (context.has("userRequirement")) {
            messages.add(new LlmService.Message("user", context.get("userRequirement").asText()));
        }
        if (context.has("requirementAnalysis")) {
            messages.add(new LlmService.Message("assistant", context.get("requirementAnalysis").asText()));
        }
        messages.add(new LlmService.Message("user",
                "根据上面的需求，请从可用表中推荐需要用到的源表，并说明原因。用户补充说明：" + userMessage));

        log.debug("源表推荐 - 调用 LLM");
        return llmService.chatStream(prompt, messages, onToken);
    }

    private String processStepDesign(ChatSession session, String userMessage,
                                      ObjectNode context, Consumer<String> onToken) throws Exception {
        String requirementJson = context.has("userRequirement")
                ? context.get("userRequirement").asText() : "";
        String tableInfo = context.has("tableRecommendation")
                ? context.get("tableRecommendation").asText() : "";

        String prompt = promptBuilder.buildStepDesignPrompt(requirementJson, tableInfo);

        List<LlmService.Message> messages = buildHistoryMessages(context,
                new String[]{"userRequirement", "requirementAnalysis", "tableRecommendation"},
                new String[]{"user", "assistant", "assistant"});
        messages.add(new LlmService.Message("user",
                "请基于以上分析，设计完整的 ETL 作业方案（包含 Extract/Transform/Load 三阶段）。用户补充：" + userMessage));

        log.debug("步骤设计 - 调用 LLM");
        return llmService.chatStream(prompt, messages, onToken);
    }

    private String processSqlGenerate(ChatSession session, String userMessage,
                                       ObjectNode context, Consumer<String> onToken) throws Exception {
        String stepJson = context.has("stepDesign")
                ? context.get("stepDesign").asText() : "";

        List<TableMetadata> tables = metadataService.listAllTables(session.getProjectId());
        List<ColumnMetadata> allColumns = new ArrayList<>();
        for (TableMetadata table : tables) {
            allColumns.addAll(metadataService.listColumns(table.getId()));
        }
        String tableMetadata = promptBuilder.buildTableMetadataForSql(tables, allColumns);

        String prompt = promptBuilder.buildSqlGeneratePrompt(stepJson, tableMetadata);

        List<LlmService.Message> messages = buildHistoryMessages(context,
                new String[]{"userRequirement", "requirementAnalysis", "stepDesign"},
                new String[]{"user", "assistant", "assistant"});
        messages.add(new LlmService.Message("user",
                "请根据以上步骤设计，生成完整的 ETL 作业 SQL（包括 Extract/Transform/Load 三阶段的所有 DDL 和 DML）。用户补充：" + userMessage));

        log.debug("SQL 生成 - 调用 LLM");
        return llmService.chatStream(prompt, messages, onToken);
    }

    private String processSqlReview(ChatSession session, String userMessage,
                                     ObjectNode context, Consumer<String> onToken) throws Exception {
        String jobName = context.has("userRequirement")
                ? context.get("userRequirement").asText() : "ETL作业";
        String generatedSql = context.has("generatedSql")
                ? context.get("generatedSql").asText() : "";

        String prompt = promptBuilder.buildSqlReviewPrompt(jobName, "", generatedSql);

        List<LlmService.Message> messages = buildHistoryMessages(context,
                new String[]{"userRequirement", "stepDesign", "generatedSql"},
                new String[]{"user", "assistant", "assistant"});
        messages.add(new LlmService.Message("user",
                "请审查以上 SQL，检查正确性和规范性，给出修改建议。用户说明：" + userMessage));

        log.debug("SQL 审查 - 调用 LLM");
        return llmService.chatStream(prompt, messages, onToken);
    }

    private String processFreeChat(ChatSession session, String userMessage,
                                    ObjectNode context, Consumer<String> onToken) throws Exception {
        List<LlmService.Message> messages = new ArrayList<>();
        List<ChatMessage> history = listMessages(session.getId());
        int start = Math.max(0, history.size() - 10);
        for (int i = start; i < history.size(); i++) {
            ChatMessage msg = history.get(i);
            messages.add(new LlmService.Message(msg.getRole(), msg.getContent()));
        }
        messages.add(new LlmService.Message("user", userMessage));

        String systemPrompt = "你是一个数据仓库 ETL 开发助手。用户的作业已经生成完毕，可以继续追问或修改 SQL。";
        log.debug("自由对话 - 调用 LLM");
        return llmService.chatStream(systemPrompt, messages, onToken);
    }

    // ========== 工具方法 ==========

    /** 独立事务保存消息（避免长事务包裹流式IO）*/
    @Transactional(rollbackFor = Exception.class)
    public void saveMessageTx(Long sessionId, String role, String content, String messageType) {
        saveMessage(sessionId, role, content, messageType);
    }

    /**
     * 获取会话中已生成的 SQL（供前端 SQL 预览使用）
     */
    public String getGeneratedSql(Long sessionId) {
        ChatSession session = getSession(sessionId);
        ObjectNode context = parseContext(session.getContextJson());
        if (context.has("generatedSql")) {
            return context.get("generatedSql").asText();
        }
        return null;
    }

    /**
     * 从 AI 响应中提取 ```json:context ... ``` 代码块的内容
     * 返回 JSON 字符串，如果没有找到则返回 null
     */
    static String extractJsonContext(String response) {
        if (response == null || response.isEmpty()) return null;
        // 兼容各种换行符和格式：```json:context ... ```
        Pattern pattern = Pattern.compile("```json:context\\s*\\r?\\n([\\s\\S]*?)\\r?\\n\\s*```");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            String result = matcher.group(1).trim();
            log.debug("[Extract] json:context 提取成功, 长度={}", result.length());
            return result;
        }
        log.debug("[Extract] 未找到 json:context 块");
        return null;
    }

    /**
     * 从 AI 响应中提取所有 ```sql ... ``` 代码块，合并返回
     * 返回合并后的 SQL 字符串，如果没有找到则返回 null
     */
    static String extractSqlBlocks(String response) {
        if (response == null || response.isEmpty()) return null;
        // 兼容 ```sql 或 ```SQL，兼容各种换行符
        Pattern pattern = Pattern.compile("```[sS][qQ][lL]\\s*\\r?\\n([\\s\\S]*?)\\r?\\n\\s*```");
        Matcher matcher = pattern.matcher(response);
        StringBuilder sb = new StringBuilder();
        int count = 0;
        while (matcher.find()) {
            count++;
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(matcher.group(1).trim());
        }
        if (count > 0) {
            log.info("[Extract] 提取到 {} 个 SQL 代码块, 总长度={}", count, sb.length());
        } else {
            log.warn("[Extract] 未找到 SQL 代码块！响应前200字符: {}",
                    response.substring(0, Math.min(200, response.length())));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * 从 context 中按 key 顺序构建历史消息列表
     */
    private List<LlmService.Message> buildHistoryMessages(ObjectNode context,
                                                           String[] keys, String[] roles) {
        List<LlmService.Message> messages = new ArrayList<>();
        for (int i = 0; i < keys.length; i++) {
            if (context.has(keys[i])) {
                String content = context.get(keys[i]).asText();
                if (content != null && !content.isEmpty()) {
                    messages.add(new LlmService.Message(roles[i], content));
                }
            }
        }
        return messages;
    }

    private ObjectNode parseContext(String contextJson) {
        try {
            return (ObjectNode) objectMapper.readTree(contextJson);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private void saveMessage(Long sessionId, String role, String content, String messageType) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
    }
}
