package com.crs.bookingservice.service.impl;

import com.crs.bookingservice.client.GeminiClient;
import com.crs.bookingservice.client.dto.gemini.GeminiGenerateContentRequest;
import com.crs.bookingservice.client.dto.gemini.GeminiGenerateContentResponse;
import com.crs.bookingservice.dto.ai.VehicleInspectionNormalizedResult;
import com.crs.bookingservice.dto.request.HandoverVehiclePhotoRequest;
import com.crs.bookingservice.entity.HandoverProtocol;
import com.crs.bookingservice.entity.VehicleInspectionAnalysis;
import com.crs.bookingservice.enums.InspectionSeverity;
import com.crs.bookingservice.repository.HandoverProtocolRepository;
import com.crs.bookingservice.repository.VehicleInspectionAnalysisRepository;
import com.crs.bookingservice.service.VehicleInspectionAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.crs.bookingservice.dto.ai.VehicleInspectionComparisonResult;
import java.util.HashSet;
import java.util.Set;
import com.crs.bookingservice.entity.IncurredFee;
import com.crs.bookingservice.entity.RentalUnit;
import com.crs.bookingservice.entity.SurchargePolicy;
import com.crs.bookingservice.enums.FeeApprovalStatus;
import com.crs.bookingservice.repository.IncurredFeeRepository;
import com.crs.bookingservice.repository.RentalUnitRepository;
import com.crs.bookingservice.repository.SurchargePolicyRepository;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.ArrayList;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleInspectionAiServiceImpl implements VehicleInspectionAiService {

    private final GeminiClient geminiClient;
    private final VehicleInspectionAnalysisRepository vehicleInspectionAnalysisRepository;
    private final HandoverProtocolRepository handoverProtocolRepository;
    private final ObjectMapper objectMapper;
    private final IncurredFeeRepository incurredFeeRepository;
    private final RentalUnitRepository rentalUnitRepository;
    private final SurchargePolicyRepository surchargePolicyRepository;

    @Value("${ai.gemini.enabled:true}")
    private boolean geminiEnabled;

    @Value("${ai.gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Override
    public void analyzeHandoverSafely(
            Long bookingId,
            Long rentalUnitId,
            String stage,
            List<HandoverVehiclePhotoRequest> vehiclePhotos
    ) {
        if (!geminiEnabled) {
            log.debug("[Gemini] Disabled. Skip analyze bookingId={}, rentalUnitId={}, stage={}",
                    bookingId, rentalUnitId, stage);
            return;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[Gemini] Missing API key. Skip analyze bookingId={}, rentalUnitId={}, stage={}",
                    bookingId, rentalUnitId, stage);
            return;
        }

        if (vehiclePhotos == null || vehiclePhotos.isEmpty()) {
            log.warn("[Gemini] Empty vehiclePhotos. Skip analyze bookingId={}, rentalUnitId={}, stage={}",
                    bookingId, rentalUnitId, stage);
            return;
        }

        String prompt = buildPrompt(stage, vehiclePhotos);
        GeminiGenerateContentRequest request = buildVisionRequest(prompt, vehiclePhotos);

        Long handoverProtocolId = handoverProtocolRepository
                .findByRentalUnitIdAndType(rentalUnitId, stage)
                .map(HandoverProtocol::getId)
                .orElse(null);

        try {
            GeminiGenerateContentResponse response = geminiClient.generateContent(model, apiKey, request);

            String rawResponseJson = safeToJson(response);
            String aiText = extractText(response);

            VehicleInspectionNormalizedResult normalized = parseNormalizedOrFallback(aiText);

            Long baselineAnalysisId = null;
            String comparisonResultJson = null;

            if ("RETURN".equalsIgnoreCase(stage)) {
                Optional<VehicleInspectionAnalysis> baselineOpt =
                        vehicleInspectionAnalysisRepository
                                .findFirstByRentalUnitIdAndStageAndAnalysisStatusOrderByCreatedAtDesc(
                                        rentalUnitId, "PICKUP", "SUCCESS");

                VehicleInspectionComparisonResult comparison =
                        buildReturnComparison(normalized, baselineOpt.orElse(null));

                baselineAnalysisId = comparison.getBaselineAnalysisId();
                comparisonResultJson = safeToJson(comparison);

                if (!Boolean.TRUE.equals(comparison.getBaselineFound())) {
                    normalized.setNeedsManualReview(true);
                    if (normalized.getSummary() == null || normalized.getSummary().isBlank()) {
                        normalized.setSummary("Thiếu baseline PICKUP, cần staff review thủ công");
                    }
                }
            }

            String normalizedJson = safeToJson(normalized);

            // saveAnalysisSuccess(
            VehicleInspectionAnalysis savedAnalysis = saveAnalysisSuccess(
                    bookingId,
                    rentalUnitId,
                    handoverProtocolId,
                    stage,
                    normalized,
                    normalizedJson,
                    rawResponseJson,
                    baselineAnalysisId,
                    comparisonResultJson
            );
            if ("RETURN".equalsIgnoreCase(stage)) {
                createAiFeeSuggestionForReturn(rentalUnitId, normalized, savedAnalysis);
            }

            log.info("[Gemini] Analyze success bookingId={}, rentalUnitId={}, stage={}, severity={}, confidence={}",
                    bookingId, rentalUnitId, stage, normalized.getSeverity(), normalized.getConfidence());

        } catch (FeignException.TooManyRequests ex) {
            saveAnalysisFailure(bookingId, rentalUnitId, handoverProtocolId, stage, "RATE_LIMIT", ex.getMessage());
            log.warn("[Gemini] Rate-limit/quota (429) bookingId={}, rentalUnitId={}, stage={}, message={}",
                    bookingId, rentalUnitId, stage, ex.getMessage());

        } catch (RetryableException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof SocketTimeoutException) {
                saveAnalysisFailure(bookingId, rentalUnitId, handoverProtocolId, stage, "TIMEOUT", ex.getMessage());
                log.warn("[Gemini] Timeout bookingId={}, rentalUnitId={}, stage={}, message={}",
                        bookingId, rentalUnitId, stage, ex.getMessage());
            } else {
                saveAnalysisFailure(bookingId, rentalUnitId, handoverProtocolId, stage, "RETRYABLE_ERROR", ex.getMessage());
                log.warn("[Gemini] Retryable error bookingId={}, rentalUnitId={}, stage={}, message={}",
                        bookingId, rentalUnitId, stage, ex.getMessage());
            }

        } catch (FeignException ex) {
            String code = ex.status() == 429 ? "RATE_LIMIT" : "FEIGN_" + ex.status();
            saveAnalysisFailure(bookingId, rentalUnitId, handoverProtocolId, stage, code, ex.getMessage());
            log.warn("[Gemini] Feign error status={} bookingId={}, rentalUnitId={}, stage={}, message={}",
                    ex.status(), bookingId, rentalUnitId, stage, ex.getMessage());

        } catch (Exception ex) {
            saveAnalysisFailure(bookingId, rentalUnitId, handoverProtocolId, stage, "UNEXPECTED_ERROR", ex.getMessage());
            log.error("[Gemini] Unexpected error bookingId={}, rentalUnitId={}, stage={}",
                    bookingId, rentalUnitId, stage, ex);
        }
    }

    private String buildPrompt(String stage, List<HandoverVehiclePhotoRequest> vehiclePhotos) {
        String photosText = vehiclePhotos.stream()
                .map(p -> p.getCorner() + ": " + p.getImageUrl())
                .collect(Collectors.joining("\n"));

        return """
                Bạn là hệ thống giám định ngoại thất xe cho nghiệp vụ thuê xe.
                Giai đoạn: %s

                Danh sách ảnh 4 góc:
                %s

                NHIỆM VỤ:
                - Đánh giá có hư hại hay không từ ảnh.
                - Nếu có hư hại thì liệt kê trong damages.

                BẮT BUỘC ĐẦU RA:
                - Chỉ trả về DUY NHẤT 1 JSON object hợp lệ.
                - Không dùng markdown.
                - Không dùng ```json.
                - Không thêm bất kỳ chữ nào ngoài JSON.
                - Tất cả key phải đúng chính tả theo schema dưới đây.

                Schema bắt buộc:
                {
                "damageDetected": boolean,
                "damages": [
                    {
                    "part": "string",
                    "type": "string",
                    "severity": "NONE|LOW|MEDIUM|HIGH",
                    "estimatedFee": number,
                    "note": "string"
                    }
                ],
                "severity": "NONE|LOW|MEDIUM|HIGH",
                "confidence": number,
                "needsManualReview": boolean,
                "recommendedFee": number,
                "summary": "string"
                }

                Quy tắc:
                - confidence trong [0, 1].
                - Nếu không chắc chắn: severity=MEDIUM, needsManualReview=true.
                - Nếu không có hư hại: damageDetected=false, damages=[],
                recommendedFee=0, severity=NONE.
                
                Lưu ý: Chỉ liệt kê các vết hư hại thật sự rõ ràng. Phần mô tả (description) trong mảng damages phải cực kỳ ngắn gọn, không vượt quá 15 từ. Chỉ trả về đúng cấu trúc JSON, không giải thích gì thêm."
                """.formatted(stage, photosText);
    }

    private String extractText(GeminiGenerateContentResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return "NO_CANDIDATE";
        }
        GeminiGenerateContentResponse.Candidate candidate = response.getCandidates().get(0);
        if (candidate.getContent() == null
                || candidate.getContent().getParts() == null
                || candidate.getContent().getParts().isEmpty()) {
            return "NO_PART";
        }
        String text = candidate.getContent().getParts().get(0).getText();
        return text == null ? "EMPTY_TEXT" : text;
    }
    private VehicleInspectionNormalizedResult parseNormalizedOrFallback(String aiText) {
        String cleaned = cleanupJsonFence(aiText);

        try {
            VehicleInspectionNormalizedResult parsed =
                    objectMapper.readValue(cleaned, VehicleInspectionNormalizedResult.class);

            if (parsed.getDamages() == null) {
                parsed.setDamages(List.of());
            }
            if (parsed.getSeverity() == null) {
                parsed.setSeverity(InspectionSeverity.MEDIUM);
            }
            if (parsed.getConfidence() == null) {
                parsed.setConfidence(BigDecimal.valueOf(0.5));
            }
            if (parsed.getNeedsManualReview() == null) {
                parsed.setNeedsManualReview(true);
            }
            if (parsed.getRecommendedFee() == null) {
                parsed.setRecommendedFee(BigDecimal.ZERO);
            }
            if (parsed.getSummary() == null || parsed.getSummary().isBlank()) {
                parsed.setSummary("AI không trả summary rõ ràng");
            }
            if (parsed.getDamageDetected() == null) {
                parsed.setDamageDetected(!parsed.getDamages().isEmpty());
            }
            return parsed;

        } catch (Exception ex) {
            VehicleInspectionNormalizedResult fallback = new VehicleInspectionNormalizedResult();
            fallback.setDamageDetected(true);
            fallback.setDamages(List.of());
            fallback.setSeverity(InspectionSeverity.MEDIUM);
            fallback.setConfidence(BigDecimal.valueOf(0.3));
            fallback.setNeedsManualReview(true);
            fallback.setRecommendedFee(BigDecimal.ZERO);
            fallback.setSummary("Không parse được JSON từ Gemini, cần staff review thủ công");
            return fallback;
        }
    }

    private String cleanupJsonFence(String text) {
        if (text == null) {
            return "{}";
        }
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }
        return cleaned;
    }

    private String safeToJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + ex.getOriginalMessage() + "\"}";
        }
    }

    // private void saveAnalysisSuccess(
    private VehicleInspectionAnalysis saveAnalysisSuccess(
            Long bookingId,
            Long rentalUnitId,
            Long handoverProtocolId,
            String stage,
            VehicleInspectionNormalizedResult normalized,
            String normalizedJson,
            String rawResponseJson,
            Long baselineAnalysisId,
            String comparisonResultJson
    ) {
        VehicleInspectionAnalysis analysis = VehicleInspectionAnalysis.builder()
                .bookingId(bookingId)
                .rentalUnitId(rentalUnitId)
                .handoverProtocolId(handoverProtocolId)
                .stage(stage)
                .provider("GEMINI")
                .model(model)
                .analysisStatus("SUCCESS")
                .damageDetected(Boolean.TRUE.equals(normalized.getDamageDetected()))
                .severity(normalized.getSeverity())
                .confidence(normalized.getConfidence())
                .needsManualReview(Boolean.TRUE.equals(normalized.getNeedsManualReview()))
                .recommendedFee(normalized.getRecommendedFee())
                .summary(normalized.getSummary())
                .damagesJson(safeToJson(normalized.getDamages()))
                .normalizedResultJson(normalizedJson)
                .rawResponseJson(rawResponseJson)
                .baselineAnalysisId(baselineAnalysisId)
                .comparisonResultJson(comparisonResultJson)
                .build();
        // vehicleInspectionAnalysisRepository.save(analysis);
        return vehicleInspectionAnalysisRepository.save(analysis);
    }

    private void saveAnalysisFailure(
            Long bookingId,
            Long rentalUnitId,
            Long handoverProtocolId,
            String stage,
            String status,
            String errorMessage
    ) {
        VehicleInspectionAnalysis analysis = VehicleInspectionAnalysis.builder()
                .bookingId(bookingId)
                .rentalUnitId(rentalUnitId)
                .handoverProtocolId(handoverProtocolId)
                .baselineAnalysisId(null)
                .comparisonResultJson(null)
                .stage(stage)
                .provider("GEMINI")
                .model(model)
                .analysisStatus(status)
                .damageDetected(false)
                .severity(InspectionSeverity.MEDIUM)
                .confidence(BigDecimal.ZERO)
                .needsManualReview(true)
                .recommendedFee(BigDecimal.ZERO)
                .summary("AI thất bại, chuyển staff review thủ công")
                .damagesJson("[]")
                .normalizedResultJson("{}")
                .rawResponseJson("{}")
                .errorMessage(errorMessage)
                .build();
        vehicleInspectionAnalysisRepository.save(analysis);
    }
    private VehicleInspectionComparisonResult buildReturnComparison(
            VehicleInspectionNormalizedResult returnResult,
            VehicleInspectionAnalysis baselineAnalysis
    ) {
        VehicleInspectionComparisonResult comparison = new VehicleInspectionComparisonResult();

        if (baselineAnalysis == null) {
            comparison.setBaselineFound(false);
            comparison.setBaselineAnalysisId(null);
            comparison.setBaselineCreatedAt(null);
            comparison.setBaselineDamageCount(0);

            int returnCount = returnResult.getDamages() == null ? 0 : returnResult.getDamages().size();
            comparison.setReturnDamageCount(returnCount);
            comparison.setNewDamageCount(returnCount);
            comparison.setNewDamageDetected(returnCount > 0);
            comparison.setNewDamages(returnResult.getDamages() == null ? List.of() : returnResult.getDamages());
            comparison.setSummary("Không tìm thấy baseline PICKUP thành công cho rentalUnit, cần staff review thủ công.");
            return comparison;
        }

        VehicleInspectionNormalizedResult baselineNormalized =
                readNormalizedFromJson(baselineAnalysis.getNormalizedResultJson());

        if (baselineNormalized == null) {
            comparison.setBaselineFound(false);
            comparison.setBaselineAnalysisId(baselineAnalysis.getId());
            comparison.setBaselineCreatedAt(baselineAnalysis.getCreatedAt());
            comparison.setBaselineDamageCount(0);

            int returnCount = returnResult.getDamages() == null ? 0 : returnResult.getDamages().size();
            comparison.setReturnDamageCount(returnCount);
            comparison.setNewDamageCount(returnCount);
            comparison.setNewDamageDetected(returnCount > 0);
            comparison.setNewDamages(returnResult.getDamages() == null ? List.of() : returnResult.getDamages());
            comparison.setSummary("Baseline PICKUP có dữ liệu lỗi định dạng, cần staff review thủ công.");
            return comparison;
        }

        List<VehicleInspectionNormalizedResult.DamageItem> baselineDamages =
                baselineNormalized.getDamages() == null ? List.of() : baselineNormalized.getDamages();
        List<VehicleInspectionNormalizedResult.DamageItem> returnDamages =
                returnResult.getDamages() == null ? List.of() : returnResult.getDamages();

        Set<String> baselineKeys = new HashSet<>();
        for (VehicleInspectionNormalizedResult.DamageItem d : baselineDamages) {
            baselineKeys.add(damageKey(d));
        }

        List<VehicleInspectionNormalizedResult.DamageItem> newDamages = returnDamages.stream()
                .filter(d -> !baselineKeys.contains(damageKey(d)))
                .toList();

        comparison.setBaselineFound(true);
        comparison.setBaselineAnalysisId(baselineAnalysis.getId());
        comparison.setBaselineCreatedAt(baselineAnalysis.getCreatedAt());
        comparison.setBaselineDamageCount(baselineDamages.size());
        comparison.setReturnDamageCount(returnDamages.size());
        comparison.setNewDamageCount(newDamages.size());
        comparison.setNewDamageDetected(!newDamages.isEmpty());
        comparison.setNewDamages(newDamages);
        comparison.setSummary(newDamages.isEmpty()
                ? "Không phát hiện hư hại mới so với PICKUP."
                : "Phát hiện " + newDamages.size() + " hư hại mới so với PICKUP.");

        return comparison;
    }

    private VehicleInspectionNormalizedResult readNormalizedFromJson(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return null;
        }
        try {
            return objectMapper.readValue(json, VehicleInspectionNormalizedResult.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String damageKey(VehicleInspectionNormalizedResult.DamageItem item) {
        if (item == null) {
            return "UNKNOWN|UNKNOWN";
        }
        return safeUpper(item.getPart()) + "|" + safeUpper(item.getType());
    }

    private String safeUpper(String value) {
        if (value == null) {
            return "UNKNOWN";
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? "UNKNOWN" : cleaned.toUpperCase();
    }

    private void createAiFeeSuggestionForReturn(
            Long rentalUnitId,
            VehicleInspectionNormalizedResult normalized,
            VehicleInspectionAnalysis savedAnalysis
    ) {
        if (savedAnalysis == null || savedAnalysis.getId() == null) {
            return;
        }
        if (incurredFeeRepository.existsByAiAnalysisId(savedAnalysis.getId())) {
            return;
        }

        BigDecimal aiRecommended = normalized.getRecommendedFee() == null
                ? BigDecimal.ZERO
                : normalized.getRecommendedFee();

        String policyCode = resolvePolicyCodeBySeverity(normalized.getSeverity());

        // Ưu tiên 1: policy riêng của rental unit theo đúng code severity
        Optional<SurchargePolicy> policyOpt = surchargePolicyRepository
                .findFirstByRentalUnitIdAndCodeIgnoreCaseOrderByIdAsc(rentalUnitId, policyCode);

        // Ưu tiên 2: global template theo code severity (rental_unit = NULL)
        if (policyOpt.isEmpty()) {
            policyOpt = surchargePolicyRepository
                    .findFirstByRentalUnitIsNullAndCodeIgnoreCaseOrderByIdAsc(policyCode);
        }

        // Ưu tiên 3: bất kỳ policy nào của rental unit
        if (policyOpt.isEmpty()) {
            policyOpt = surchargePolicyRepository.findFirstByRentalUnitIdOrderByIdAsc(rentalUnitId);
        }

        BigDecimal policyAmount = policyOpt.map(SurchargePolicy::getFeeAmount).orElse(BigDecimal.ZERO);
        BigDecimal suggestedFee = calculateSuggestedFeeFromPolicyAndAi(policyAmount, aiRecommended);

        if (suggestedFee.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        RentalUnit unit = rentalUnitRepository.findById(rentalUnitId).orElse(null);
        if (unit == null) {
            return;
        }

        IncurredFee pendingFee = IncurredFee.builder()
                .rentalUnit(unit)
                .surchargePolicy(policyOpt.orElse(null))
                .totalFee(suggestedFee)
                .description(buildAiFeeDescription(normalized, policyCode))
                .aiSuggested(true)
                .approvalStatus(FeeApprovalStatus.PENDING)
                .aiAnalysisId(savedAnalysis.getId())
                .build();

        incurredFeeRepository.save(pendingFee);
    }

    private String resolvePolicyCodeBySeverity(InspectionSeverity severity) {
        if (severity == null) {
            return "DAMAGE_GENERAL";
        }
        return switch (severity) {
            case HIGH -> "DAMAGE_HIGH";
            case MEDIUM -> "DAMAGE_MEDIUM";
            case LOW -> "DAMAGE_LOW";
            case NONE -> "DAMAGE_GENERAL";
        };
    }

    private BigDecimal calculateSuggestedFeeFromPolicyAndAi(BigDecimal policyAmount, BigDecimal aiRecommended) {
        BigDecimal policy = policyAmount == null ? BigDecimal.ZERO : policyAmount;
        BigDecimal ai = aiRecommended == null ? BigDecimal.ZERO : aiRecommended;
        return policy.max(ai);
    }

    private String buildAiFeeDescription(VehicleInspectionNormalizedResult normalized, String policyCode) {
        String summary = normalized.getSummary() == null ? "AI suggested damage fee" : normalized.getSummary();
        return "[AI_SUGGESTED][PENDING_APPROVAL] policy=" + policyCode + " | " + summary;
    }

    private GeminiGenerateContentRequest buildVisionRequest(
            String prompt,
            List<HandoverVehiclePhotoRequest> vehiclePhotos
    ) {
        List<GeminiGenerateContentRequest.ImagePayload> images = new ArrayList<>();

        for (HandoverVehiclePhotoRequest photo : vehiclePhotos) {
            images.add(downloadImageAsPayload(photo.getImageUrl()));
        }

        // Defensive check: bài toán này bắt buộc đủ 4 ảnh
        if (images.size() != 4) {
            throw new IllegalStateException("Không tải đủ 4 ảnh để gửi Gemini Vision");
        }

        return GeminiGenerateContentRequest.fromPromptAndImages(prompt, images);
    }

    private GeminiGenerateContentRequest.ImagePayload downloadImageAsPayload(String imageUrl) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(imageUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("Image URL trả về status " + code + ": " + imageUrl);
            }

            String mimeType = conn.getContentType();
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = inferMimeTypeFromUrl(imageUrl);
            }

            byte[] bytes;
            try (InputStream in = conn.getInputStream();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                bytes = out.toByteArray();
            }

            String base64 = Base64.getEncoder().encodeToString(bytes);
            return GeminiGenerateContentRequest.ImagePayload.builder()
                    .mimeType(mimeType)
                    .base64Data(base64)
                    .build();

        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tải ảnh từ URL: " + imageUrl, ex);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String inferMimeTypeFromUrl(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }
}