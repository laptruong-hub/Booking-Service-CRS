package com.crs.bookingservice.client;

import com.crs.bookingservice.client.dto.gemini.GeminiGenerateContentRequest;
import com.crs.bookingservice.client.dto.gemini.GeminiGenerateContentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client gọi Google Gemini Generate Content API.
 */
@FeignClient(name = "gemini-client", url = "${ai.gemini.base-url}")
public interface GeminiClient {

    /**
     * POST /v1beta/models/{model}:generateContent?key=API_KEY
     */
    @PostMapping(value = "/v1beta/models/{model}:generateContent", consumes = "application/json")
    GeminiGenerateContentResponse generateContent(
            @PathVariable("model") String model,
            @RequestParam("key") String apiKey,
            @RequestBody GeminiGenerateContentRequest request);
}