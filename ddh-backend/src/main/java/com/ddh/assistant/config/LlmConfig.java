package com.ddh.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM API 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class LlmConfig {

    /**
     * API Key
     */
    private String apiKey;

    /**
     * API Base URL（代理地址）
     */
    private String baseUrl;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 最大 token 数
     */
    private int maxTokens = 4096;

    /**
     * 温度参数
     */
    private double temperature = 0.7;

    /**
     * 超时时间（秒）
     */
    private int timeout = 120;
}
