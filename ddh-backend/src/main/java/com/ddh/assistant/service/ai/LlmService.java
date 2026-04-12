package com.ddh.assistant.service.ai;

import org.springframework.stereotype.Service;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.ddh.assistant.config.LlmConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * LLM 服务（兼容 OpenAI 格式的代理 API）
 */
@Slf4j
@Service
public class LlmService {

    private final LlmConfig config;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    public LlmService(LlmConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(config.getTimeout()))
                .readTimeout(Duration.ofSeconds(config.getTimeout() * 2))
                .writeTimeout(Duration.ofSeconds(config.getTimeout()))
                .build();
        // 启动时打印配置（Key 只显示前8位）
        String key = config.getApiKey();
        String keyPreview = (key != null && key.length() > 8)
                ? key.substring(0, 8) + "...(len=" + key.length() + ")"
                : "[empty or null]";
        log.info("LlmService 初始化 - baseUrl={}, model={}, apiKey={}",
                config.getBaseUrl(), config.getModel(), keyPreview);
    }

    /**
     * 同步聊天（非流式，用于内部逻辑）
     */
    public String chat(String systemPrompt, List<Message> messages) throws IOException {
        List<Message> allMessages = buildMessages(systemPrompt, messages);
        return doChat(allMessages);
    }

    /**
     * 流式聊天 - 每收到一个 token 就调用 onToken 回调
     *
     * @param systemPrompt 系统提示词
     * @param messages     对话历史
     * @param onToken      token 回调（在 IO 线程中调用，注意线程安全）
     * @return 完整的响应内容
     */
    public String chatStream(String systemPrompt, List<Message> messages,
                              Consumer<String> onToken) throws IOException {
        List<Message> allMessages = buildMessages(systemPrompt, messages);
        return doStreamChat(allMessages, onToken);
    }

    private List<Message> buildMessages(String systemPrompt, List<Message> messages) {
        List<Message> allMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            allMessages.add(new Message("system", systemPrompt));
        }
        allMessages.addAll(messages);
        return allMessages;
    }

    private Request buildRequest(List<Message> messages, boolean stream) throws IOException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.getModel());
//        requestBody.put("max_tokens", config.getMaxTokens());
//        requestBody.put("temperature", config.getTemperature());
        requestBody.put("stream", stream);
        if (!stream) {
            // enable_thinking 仅在非流式模式下启用，流式模式可能不兼容
            requestBody.put("enable_thinking", true);
        }

        List<Map<String, String>> msgList = new ArrayList<>();
        for (Message m : messages) {
            Map<String, String> msg = new LinkedHashMap<>();
            msg.put("role", m.getRole());
            msg.put("content", m.getContent());
            msgList.add(msg);
        }
        requestBody.put("messages", msgList);

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(requestBody),
                MediaType.get("application/json; charset=utf-8")
        );

        return new Request.Builder()
                .url(config.getBaseUrl() + "/chat/completions")
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://ddh-assistant.local")
                .addHeader("X-Title", "DDH-Assistant")
                .post(body)
                .build();
    }

    private String doChat(List<Message> messages) throws IOException {
        Request request = buildRequest(messages, false);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("LLM API 错误: {} - {}", response.code(), errorBody);
                throw new IOException("LLM API 返回错误 " + response.code() + ": " + errorBody);
            }

            JsonNode root = objectMapper.readTree(response.body().string());
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IOException("No response from API");
            }

            return choices.get(0).get("message").get("content").asText();
        }
    }

    /**
     * 真正的流式实现：逐行读取 SSE，每个 token 立即回调
     */
    private String doStreamChat(List<Message> messages, Consumer<String> onToken) throws IOException {
        Request request = buildRequest(messages, true);
        StringBuilder fullContent = new StringBuilder();
        int lineCount = 0;
        int tokenCount = 0;

        log.info("[Stream] 开始流式请求, model={}", config.getModel());

        try (Response response = httpClient.newCall(request).execute()) {
            log.info("[Stream] 收到响应, code={}, contentType={}",
                    response.code(),
                    response.header("Content-Type"));

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                log.error("[Stream] LLM API 流式错误: {} - {}", response.code(), errorBody);
                throw new IOException("LLM API 返回错误 " + response.code() + ": " + errorBody);
            }

            if (response.body() == null) {
                throw new IOException("响应体为空");
            }

            // 逐行读取流式响应
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineCount++;
                    // 前 5 行打印完整内容用于调试格式
                    if (lineCount <= 5) {
                        log.info("[Stream] 原始行[{}]: '{}'", lineCount, line);
                    }

                    // 跳过空行和 SSE 注释行（以 : 开头）
                    if (line.isEmpty() || (line.startsWith(":") && !line.startsWith("data:"))) {
                        continue;
                    }

                    if (!line.startsWith("data:")) continue;

                    String data = line.substring(5).trim();
                    if (data.isEmpty()) continue;
                    if ("[DONE]".equals(data)) {
                        log.info("[Stream] 收到 [DONE] 信号");
                        continue;
                    }

                    try {
                        JsonNode node = objectMapper.readTree(data);
                        JsonNode choices = node.path("choices");
                        if (choices.isEmpty()) continue;

                        JsonNode delta = choices.get(0).path("delta");
                        String token = delta.path("content").asText(null);

                        if (token != null && !token.isEmpty()) {
                            tokenCount++;
                            fullContent.append(token);
                            if (tokenCount <= 3) {
                                log.info("[Stream] token[{}]: '{}'", tokenCount, token);
                            }
                            if (onToken != null) {
                                onToken.accept(token);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("[Stream] 解析 SSE 行失败: '{}', error: {}", line, e.getMessage());
                    }
                }
            }
        }

        log.info("[Stream] 流式完成: 共 {} 行, {} 个 token, 内容长度 {} 字符",
                lineCount, tokenCount, fullContent.length());
        if (fullContent.length() > 0) {
            log.info("[Stream] 内容前100字符: {}",
                    fullContent.substring(0, Math.min(100, fullContent.length())));
        } else {
            log.warn("[Stream] 警告: 流式响应内容为空！");
        }

        return fullContent.toString();
    }

    /**
     * 消息类
     */
    public static class Message {
        private String role;
        private String content;

        public Message() {}
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
