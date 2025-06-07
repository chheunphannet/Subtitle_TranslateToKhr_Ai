package com.translatesubtitle.khmertranslateAi.dto;

import java.util.List;

// Using record for immutable DTOs (Java 14+)
// If using older Java, create regular classes with getters/setters.

public class GeminiDtos {

    // Request Payload DTOs
    public record TextPart(String text) {}
    public record Content(String role, List<TextPart> parts) {}
    public record GeminiRequest(List<Content> contents) {}

    // Response Payload DTOs
    public record Candidate(Content content, String finishReason, int index, List<SafetyRating> safetyRatings) {}
    public record SafetyRating(String category, String probability) {}
    public record GeminiResponse(List<Candidate> candidates, PromptFeedback promptFeedback) {}
    public record PromptFeedback(List<SafetyRating> safetyRatings) {}

}
