package com.translatesubtitle.khmertranslateAi.serviceImpl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.translatesubtitle.khmertranslateAi.dto.GeminiDtos;
import com.translatesubtitle.khmertranslateAi.dto.SubtitleEntry;
// Assuming GeminiApiConfig will provide these new values
// import com.translatesubtitle.khmertranslateAi.config.GeminiApiConfig;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class GeminiTranslationService {
    private static final Logger logger = LoggerFactory.getLogger(GeminiTranslationService.class);
    private final WebClient geminiWebClient;
    private final GeminiApiConfig geminiApiConfig; // Make sure this class has the new properties

    // Rate-limit friendly parameters - now instance variables initialized from config
    private final int batchSize;
    private final int concurrentRequests;
    private final int maxRetries;
    private final Duration initialBackoff;
    private final Duration maxRateLimitBackoff; // Max backoff for the retry policy
    private final long delayBetweenBatchRequestMs;

    public GeminiTranslationService(WebClient geminiWebClient, GeminiApiConfig geminiApiConfig) {
        this.geminiWebClient = geminiWebClient;
        this.geminiApiConfig = geminiApiConfig;

        // Initialize from GeminiApiConfig
        // Ensure GeminiApiConfig has methods like getBatchSize(), getConcurrentRequests(), etc.
        // Add sensible defaults here or ensure they are always set in config
        this.batchSize = geminiApiConfig.getBatchSize(); // e.g., 5
        this.concurrentRequests = geminiApiConfig.getConcurrentRequests(); // e.g., 2
        this.maxRetries = geminiApiConfig.getMaxRetries(); // e.g., 4
        this.initialBackoff = Duration.ofSeconds(geminiApiConfig.getInitialBackoffSeconds()); // e.g., 2
        this.maxRateLimitBackoff = Duration.ofSeconds(geminiApiConfig.getMaxRateLimitBackoffSeconds()); // e.g., 60
        this.delayBetweenBatchRequestMs = geminiApiConfig.getDelayBetweenBatchRequestMs(); // e.g., 500
    }

    public Mono<List<SubtitleEntry>> translateSubtitles(List<SubtitleEntry> subtitleEntries, String targetLanguage) {
        if (subtitleEntries == null || subtitleEntries.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        String apiKey = geminiApiConfig.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            logger.error("Gemini API key is not configured. Please set 'gemini.api.key'.");
            return Mono.just(subtitleEntries); // Return original entries
        }

        List<List<SubtitleEntry>> batches = createBatches(subtitleEntries, this.batchSize);
        logger.info("Processing {} subtitle entries in {} batches with batch size {} and concurrency level {}.",
                subtitleEntries.size(), batches.size(), this.batchSize, this.concurrentRequests);

        return Flux.fromIterable(batches)
                .flatMap(batch -> translateBatch(batch, targetLanguage, apiKey), this.concurrentRequests)
                .collectList()
                .map(this::flattenBatches)
                .doOnSuccess(result -> logger.info("Translation completed. Processed {} entries.", result.size()))
                .doOnError(error -> logger.error("Error during overall subtitle translation process: {}", error.getMessage(), error));
    }

    private List<List<SubtitleEntry>> createBatches(List<SubtitleEntry> entries, int currentBatchSize) {
        return IntStream.range(0, (entries.size() + currentBatchSize - 1) / currentBatchSize)
                .mapToObj(i -> entries.subList(i * currentBatchSize, Math.min((i + 1) * currentBatchSize, entries.size())))
                .collect(Collectors.toList());
    }

    private Mono<List<SubtitleEntry>> translateBatch(List<SubtitleEntry> batch, String targetLanguage, String apiKey) {
        // Add small delay to avoid overwhelming the API
        return Mono.delay(Duration.ofMillis(this.delayBetweenBatchRequestMs))
                .then(performBatchTranslation(batch, targetLanguage, apiKey));
    }

    private Mono<List<SubtitleEntry>> performBatchTranslation(List<SubtitleEntry> batch, String targetLanguage, String apiKey) {
    	StringBuilder batchPrompt = new StringBuilder();
    	batchPrompt.append(String.format("Translate the following %d subtitle lines to %s. ", batch.size(), targetLanguage));
    	batchPrompt.append("IMPORTANT RULES:\n");
    	batchPrompt.append("1. Do NOT translate proper names (person names, locations, brands, etc.) — keep them in English.\n");
    	batchPrompt.append("2. If a line is unclear or difficult to translate, use the original English text.\n");
    	batchPrompt.append("3. NEVER leave empty translations - always provide something for each line.\n");
    	batchPrompt.append("4. Provide ONLY the translated text for each line, separated by '|||'.\n");
    	batchPrompt.append("5. Do NOT add any extra text, explanations, or numbering before or after the translations.\n");
    	batchPrompt.append("6. Maintain the exact same order as the input.\n\n");
    	batchPrompt.append("FORMAT EXAMPLE:\n");
    	batchPrompt.append("Input: 1. \"Hello there\" 2. \"How are you?\"\n");
    	batchPrompt.append(String.format("Output for %s: \"[Hello translation]|||[How are you translation]\"\n\n", targetLanguage));

    	batchPrompt.append(String.format("You must provide exactly %d translations separated by |||.\n", batch.size()));
    	batchPrompt.append("Here are the lines to translate:\n");

        for (int i = 0; i < batch.size(); i++) {
            batchPrompt.append(String.format("%d. \"%s\"\n", i + 1, batch.get(i).getText()));
        }

        GeminiDtos.TextPart textPart = new GeminiDtos.TextPart(batchPrompt.toString());
        GeminiDtos.Content content = new GeminiDtos.Content("user", List.of(textPart));
        GeminiDtos.GeminiRequest requestPayload = new GeminiDtos.GeminiRequest(List.of(content));

        logger.info("Translating batch of {} entries (sequences {}-{})",
                batch.size(), batch.get(0).getSequence(), batch.get(batch.size() - 1).getSequence());

        return geminiWebClient.post()
                .uri(uriBuilder -> uriBuilder.queryParam("key", apiKey).build())
                .bodyValue(requestPayload)
                .retrieve()
                .bodyToMono(GeminiDtos.GeminiResponse.class)
                .map(response -> processBatchResponse(response, batch))
                .retryWhen(Retry.backoff(this.maxRetries, this.initialBackoff)
                        .maxBackoff(this.maxRateLimitBackoff)
                        .filter(this::isRetryableError)
                        .doBeforeRetry(retrySignal -> {
                            long attempt = retrySignal.totalRetries() + 1;
                            // Use failure from retrySignal to determine specific backoff
                            Duration waitTime = getBackoffDuration(retrySignal.failure(), attempt, this.initialBackoff);
                            logger.warn("Retrying batch (sequences {}-{}) attempt {}/{} after {}ms. Reason: {}",
                                    batch.get(0).getSequence(),
                                    batch.get(batch.size() - 1).getSequence(),
                                    attempt, this.maxRetries,
                                    waitTime.toMillis(),
                                    retrySignal.failure().getMessage());
                        })
                        .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                            logger.error("Failed to translate batch (sequences {}-{}) after {} retries. Last error: {}",
                                 batch.get(0).getSequence(),
                                 batch.get(batch.size() - 1).getSequence(),
                                 this.maxRetries,
                                 retrySignal.failure().getMessage());
                            return retrySignal.failure(); // Propagate the last error
                        }))
                .onErrorResume(WebClientResponseException.class, ex -> {
                     logger.error("WebClientResponseException for batch (sequences {}-{}): {} - {}. Returning original batch.",
                        batch.get(0).getSequence(), batch.get(batch.size() -1).getSequence(),
                        ex.getStatusCode(), ex.getResponseBodyAsString());
                     return Mono.just(batch.stream().peek(entry -> entry.setTranslatedText(entry.getText() + " [API Error]")).collect(Collectors.toList()));
                })
                .onErrorReturn(Exception.class, batch.stream().peek(entry -> entry.setTranslatedText(entry.getText() + " [Fallback Error]")).collect(Collectors.toList())); // Catch-all for other errors after retries
    }

    private List<SubtitleEntry> processBatchResponse(GeminiDtos.GeminiResponse response, List<SubtitleEntry> originalBatch) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            logger.warn("Empty or null response candidates for batch (sequences {}-{}). Using original text.",
                        originalBatch.get(0).getSequence(), originalBatch.get(originalBatch.size()-1).getSequence());
            return originalBatch.stream().peek(entry -> entry.setTranslatedText(entry.getText() + " [Empty Response]")).collect(Collectors.toList());
        }

        GeminiDtos.Candidate firstCandidate = response.candidates().get(0);
        if (firstCandidate.content() == null || firstCandidate.content().parts() == null
                || firstCandidate.content().parts().isEmpty() || firstCandidate.content().parts().get(0).text() == null) {
            logger.warn("Malformed response content/parts for batch (sequences {}-{}). Using original text.",
                        originalBatch.get(0).getSequence(), originalBatch.get(originalBatch.size()-1).getSequence());
            return originalBatch.stream().peek(entry -> entry.setTranslatedText(entry.getText() + " [Malformed Response]")).collect(Collectors.toList());
        }

        String translatedText = firstCandidate.content().parts().get(0).text().trim();

        String[] translations = null;
        if (translatedText.contains("|||")) {
            // Split by ||| but preserve empty strings to maintain count
            translations = translatedText.split("\\|\\|\\|", -1); // -1 preserves empty strings
            
            // Trim each translation but keep empty ones as empty strings
            for (int i = 0; i < translations.length; i++) {
                translations[i] = translations[i].trim();
            }
            
            logger.debug("Split translations by |||: Found {} parts for batch size {}", translations.length, originalBatch.size());
        } else {
            // Fallback: split by newlines and clean up
            logger.warn("Response for batch (sequences {}-{}) did not contain '|||' separator. Falling back to newline splitting. Raw response: '{}'",
                        originalBatch.get(0).getSequence(), originalBatch.get(originalBatch.size()-1).getSequence(), translatedText);
            translations = translatedText.split("\n");
            translations = cleanupTranslations(translations);
        }

        if (translations.length != originalBatch.size()) {
            logger.warn("Translation count mismatch for batch (sequences {}-{}). Expected {}, got {}. Raw response: '{}'. Falling back to individual error marking.",
                    originalBatch.get(0).getSequence(), originalBatch.get(originalBatch.size()-1).getSequence(),
                    originalBatch.size(), translations.length, translatedText);
            return fallbackToIndividualTranslationWithErrorMarking(originalBatch, translations, translatedText);
        }

        List<SubtitleEntry> result = new ArrayList<>();
        for (int i = 0; i < originalBatch.size(); i++) {
            SubtitleEntry entry = new SubtitleEntry(originalBatch.get(i)); // Create a new entry or clone
            String translation = translations[i].trim();
            translation = translation.replaceFirst("^\\d+\\.\\s*", ""); // Remove potential numbering

            if (translation.isEmpty()) {
                 logger.warn("Empty translation for entry sequence {} in batch {}-{} after parsing. Original: '{}'. Using original with marker.",
                             entry.getSequence(), originalBatch.get(0).getSequence(), originalBatch.get(originalBatch.size()-1).getSequence(), entry.getText());
                 entry.setTranslatedText(entry.getText() + " [Empty Translation]");
            } else {
                entry.setTranslatedText(translation);
            }
            result.add(entry);
        }
        return result;
    }
    
    // Modified fallback to be more informative
    private List<SubtitleEntry> fallbackToIndividualTranslationWithErrorMarking(List<SubtitleEntry> batch, String[] parsedTranslations, String fullResponse) {
        logger.warn("Executing fallback translation for batch (sequences {}-{}) due to parsing issues. Full response: '{}'", 
                    batch.get(0).getSequence(), batch.get(batch.size() - 1).getSequence(), fullResponse);
        
        List<SubtitleEntry> result = new ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            SubtitleEntry entry = new SubtitleEntry(batch.get(i));
            
            if (i < parsedTranslations.length) {
                String translation = parsedTranslations[i] != null ? parsedTranslations[i].trim() : "";
                translation = translation.replaceFirst("^\\d+\\.\\s*", ""); // Remove potential numbering
                
                if (!translation.isEmpty()) {
                    entry.setTranslatedText(translation);
                    logger.debug("Fallback: Using partial translation for sequence {}: '{}'", entry.getSequence(), translation);
                } else {
                    entry.setTranslatedText(entry.getText() + " [Empty in Fallback]");
                    logger.debug("Fallback: Empty translation for sequence {}, using original with marker", entry.getSequence());
                }
            } else {
                entry.setTranslatedText(entry.getText() + " [No Translation in Fallback]");
                logger.debug("Fallback: No translation available for sequence {}, using original with marker", entry.getSequence());
            }
            result.add(entry);
        }
        return result;
    }


    private String[] cleanupTranslations(String[] translations) {
        List<String> cleaned = new ArrayList<>();
        for (String translation : translations) {
            String clean = translation.trim();
            // Skip empty lines and lines that are just numbers like "1.", "2."
            if (!clean.isEmpty() && !clean.matches("^\\d+\\.$")) {
                cleaned.add(clean);
            }
        }
        return cleaned.toArray(new String[0]);
    }

    // Fallback used by older logic - replaced by fallbackToIndividualTranslationWithErrorMarking
    // private List<SubtitleEntry> fallbackToIndividualTranslation(List<SubtitleEntry> batch, String fullResponse) {
    //     // ... (original implementation, can be removed if the new one is preferred)
    // }

    private boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            HttpStatus status = HttpStatus.resolve(((WebClientResponseException) throwable).getRawStatusCode());
            return status == HttpStatus.TOO_MANY_REQUESTS
                    || status == HttpStatus.SERVICE_UNAVAILABLE
                    || status == HttpStatus.INTERNAL_SERVER_ERROR
                    || status == HttpStatus.BAD_GATEWAY
                    || status == HttpStatus.GATEWAY_TIMEOUT;
        }
        return false;
    }

    private Duration getBackoffDuration(Throwable throwable, long attempt, Duration defaultInitialBackoff) {
        if (throwable instanceof WebClientResponseException wcre) {
            HttpStatus status = HttpStatus.resolve(wcre.getRawStatusCode());
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                // Check for Retry-After header (This is conceptual, actual header name might vary)
                // List<String> retryAfterHeader = wcre.getHeaders().get("Retry-After");
                // if (retryAfterHeader != null && !retryAfterHeader.isEmpty()) {
                //     try {
                //         long retryAfterSeconds = Long.parseLong(retryAfterHeader.get(0));
                //         logger.info("Utilizing Retry-After header: {} seconds", retryAfterSeconds);
                //         return Duration.ofSeconds(retryAfterSeconds);
                //     } catch (NumberFormatException e) {
                //         logger.warn("Could not parse Retry-After header value: {}", retryAfterHeader.get(0));
                //     }
                // }
                // Exponential backoff for rate limits, capped to prevent overly long waits e.g. 2^6 = 64s
                // The Retry.backoff().maxBackoff() will provide an upper ceiling for this.
                long seconds = (long) Math.pow(2, Math.min(attempt - 1, 6)); // Caps exponent at 6 (64 seconds)
                return Duration.ofSeconds(seconds);
            }
        }
        // For other retryable errors, use the default initial backoff,
        // subsequent retries will be handled by Retry.backoff's exponential strategy.
        return defaultInitialBackoff;
    }

    private List<SubtitleEntry> flattenBatches(List<List<SubtitleEntry>> processedBatches) {
        return processedBatches.stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    // Dummy GeminiApiConfig for compilation if you don't have it yet.
    // Replace with your actual GeminiApiConfig class.
    // YOU MUST PROVIDE ACTUAL IMPLEMENTATION AND CONFIGURATION FOR THIS.
    /*
    static class GeminiApiConfig {
        private String apiKey = "YOUR_API_KEY"; // Should be loaded securely
        private int batchSize = 5;
        private int concurrentRequests = 1; // Start conservative
        private int maxRetries = 3;
        private long initialBackoffSeconds = 2;
        private long maxRateLimitBackoffSeconds = 60;
        private long delayBetweenBatchRequestMs = 1000; // Start conservative (1 second)

        public String getApiKey() { return apiKey; }
        public int getBatchSize() { return batchSize; }
        public int getConcurrentRequests() { return concurrentRequests; }
        public int getMaxRetries() { return maxRetries; }
        public long getInitialBackoffSeconds() { return initialBackoffSeconds; }
        public long getMaxRateLimitBackoffSeconds() { return maxRateLimitBackoffSeconds; }
        public long getDelayBetweenBatchRequestMs() { return delayBetweenBatchRequestMs; }

        // Add setters or constructor if you load from properties
    }
    */
}