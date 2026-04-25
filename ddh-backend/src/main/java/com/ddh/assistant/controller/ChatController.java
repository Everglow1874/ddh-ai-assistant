package com.ddh.assistant.controller;

import com.ddh.assistant.common.Result;
import com.ddh.assistant.model.entity.ChatSession;
import com.ddh.assistant.model.entity.ChatMessage;
import com.ddh.assistant.service.chat.ChatService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话控制器
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * 创建对话会话
     */
    @PostMapping("/projects/{projectId}/chat/sessions")
    public Result<ChatSession> createSession(
            @PathVariable Long projectId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        
        String sessionName = body != null ? body.get("sessionName") : null;
        ChatSession session = chatService.createSession(projectId, sessionName);
        return Result.ok(session);
    }

    /**
     * 获取对话列表
     */
    @GetMapping("/projects/{projectId}/chat/sessions")
    public Result<Page<ChatSession>> listSessions(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<ChatSession> page = chatService.listSessions(projectId, current, size);
        return Result.ok(page);
    }

    /**
     * 获取单个会话
     */
    @GetMapping("/chat/sessions/{sessionId}")
    public Result<ChatSession> getSession(@PathVariable Long sessionId) {
        ChatSession session = chatService.getSession(sessionId);
        return Result.ok(session);
    }

    /**
     * 获取对话消息
     */
    @GetMapping("/chat/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long sessionId) {
        List<ChatMessage> messages = chatService.listMessages(sessionId);
        return Result.ok(messages);
    }

    /**
     * 发送消息（SSE 流式）
     */
    @PostMapping("/chat/sessions/{sessionId}/send")
    public SseEmitter sendMessage(
            @PathVariable Long sessionId,
            @RequestBody java.util.Map<String, String> body) throws IOException {
        
        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时
        
        String userMessage = body.get("message");
        if (userMessage == null || userMessage.trim().isEmpty()) {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"code\":400,\"message\":\"消息不能为空\"}"));
            emitter.complete();
            return emitter;
        }
        
        // 异步处理
        executor.execute(() -> {
            try {
                // 流式调用，每个 token 立即推送给前端
                chatService.processMessageStream(sessionId, userMessage, token -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(token));
                    } catch (IOException e) {
                        log.warn("SSE 推送 token 失败，客户端可能已断开");
                    }
                });

                // 发送完成事件
                emitter.send(SseEmitter.event().name("done").data(""));
                emitter.complete();
            } catch (Exception e) {
                log.error("处理消息失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"code\":500,\"message\":\"" + e.getMessage() + "\"}"));
                } catch (IOException ex) {
                    // ignore
                }
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /**
     * 确认当前步骤
     */
    @PostMapping("/chat/sessions/{sessionId}/confirm")
    public SseEmitter confirm(@PathVariable Long sessionId) throws IOException {
        SseEmitter emitter = new SseEmitter(300_000L);
        
        executor.execute(() -> {
            try {
                // 模拟确认后的下一步
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data("已确认，进入下一步..."));
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(""));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/chat/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        chatService.deleteSession(sessionId);
        return Result.ok();
    }

    /**
     * 获取会话中已生成的 SQL（供 SQL 预览面板使用）
     */
    @GetMapping("/chat/sessions/{sessionId}/sql")
    public Result<String> getGeneratedSql(@PathVariable Long sessionId) {
        String sql = chatService.getGeneratedSql(sessionId);
        return Result.ok(sql);
    }
}