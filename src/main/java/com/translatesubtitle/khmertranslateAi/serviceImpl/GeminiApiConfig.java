package com.translatesubtitle.khmertranslateAi.serviceImpl; // Or your common config package e.g., com.translatesubtitle.khmertranslateAi.config

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GeminiApiConfig {

    @Value("${gemini.api.key:}") // Default to empty string if not set
    private String apiKey;

    // Using the preview model URL
 // In GeminiApiConfig.java
    //(very good)
    //private final String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
    //private final String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro-preview-05-06:generateContent";
    //(good)
    private final String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-04-17:generateContent";
    //private final String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-05-20:generateContent";
    //private final String geminiApiUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    // Rate-limiting and batching parameters with defaults
    @Value("${gemini.api.concurrent-requests:1}")
    private int concurrentRequests;

    @Value("${gemini.api.delay-between-batch-request-ms:1000}")
    private long delayBetweenBatchRequestMs;

    @Value("${gemini.api.batch-size:5}")
    private int batchSize;

    @Value("${gemini.api.max-retries:3}")
    private int maxRetries;

    @Value("${gemini.api.initial-backoff-seconds:2}")
    private long initialBackoffSeconds;

    @Value("${gemini.api.max-rate-limit-backoff-seconds:60}")
    private long maxRateLimitBackoffSeconds;


    @Bean
    WebClient geminiWebClient() {
        return WebClient.builder()
                .baseUrl(geminiApiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getConcurrentRequests() {
        return concurrentRequests;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getInitialBackoffSeconds() {
        return initialBackoffSeconds;
    }

    public long getMaxRateLimitBackoffSeconds() {
        return maxRateLimitBackoffSeconds;
    }

    public long getDelayBetweenBatchRequestMs() {
        return delayBetweenBatchRequestMs;
    }

    // Optional: Add setters if you ever need to modify them programmatically,
    // but @Value injection is typical for configuration.
}