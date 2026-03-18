package com.crs.bookingservice.client.dto.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiGenerateContentRequest {

    List<Content> contents;
    GenerationConfig generationConfig;

    public static GeminiGenerateContentRequest fromTextPrompt(String prompt) {
        Part part = Part.builder().text(prompt).build();
        Content content = Content.builder()
                .role("user")
                .parts(List.of(part))
                .build();

        GenerationConfig config = GenerationConfig.builder()
                .temperature(0.2)
                .maxOutputTokens(1024)
                .responseMimeType("application/json")
                .build();

        return GeminiGenerateContentRequest.builder()
                .contents(List.of(content))
                .generationConfig(config)
                .build();
    }

    public static GeminiGenerateContentRequest fromPromptAndImages(String prompt, List<ImagePayload> images) {
        List<Part> parts = new ArrayList<>();

        // Part 1: prompt text
        parts.add(Part.builder().text(prompt).build());

        // Part 2..n: inline image data
        for (ImagePayload image : images) {
            parts.add(Part.builder()
                    .inlineData(InlineData.builder()
                            .mimeType(image.getMimeType())
                            .data(image.getBase64Data())
                            .build())
                    .build());
        }

        Content content = Content.builder()
                .role("user")
                .parts(parts)
                .build();

        GenerationConfig config = GenerationConfig.builder()
                .temperature(0.2)
                .maxOutputTokens(8192)
                .responseMimeType("application/json")
                .build();

        return GeminiGenerateContentRequest.builder()
                .contents(List.of(content))
                .generationConfig(config)
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        String role;
        List<Part> parts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Part {
        String text;
        InlineData inlineData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InlineData {
        String mimeType;
        String data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ImagePayload {
        String mimeType;
        String base64Data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerationConfig {
        Double temperature;
        Integer maxOutputTokens;
        String responseMimeType;
    }
}