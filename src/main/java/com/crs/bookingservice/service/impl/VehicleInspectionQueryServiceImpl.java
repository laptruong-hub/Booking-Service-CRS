package com.crs.bookingservice.service.impl;

import com.crs.bookingservice.dto.ai.VehicleInspectionComparisonResult;
import com.crs.bookingservice.dto.ai.VehicleInspectionNormalizedResult;
import com.crs.bookingservice.dto.response.VehicleInspectionDetailResponse;
import com.crs.bookingservice.dto.response.VehicleInspectionHistoryResponse;
import com.crs.bookingservice.entity.VehicleInspectionAnalysis;
import com.crs.bookingservice.exception.ResourceNotFoundException;
import com.crs.bookingservice.repository.VehicleInspectionAnalysisRepository;
import com.crs.bookingservice.service.VehicleInspectionQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VehicleInspectionQueryServiceImpl implements VehicleInspectionQueryService {

    private final VehicleInspectionAnalysisRepository vehicleInspectionAnalysisRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public VehicleInspectionHistoryResponse getHistoryByRentalUnit(Long rentalUnitId, String stage) {
        List<VehicleInspectionAnalysis> analyses = vehicleInspectionAnalysisRepository
                .findByRentalUnitIdOrderByCreatedAtDesc(rentalUnitId);

        List<VehicleInspectionDetailResponse> items = analyses.stream()
                .filter(a -> stage == null || stage.isBlank() || stage.equalsIgnoreCase(a.getStage()))
                .map(this::toDetailResponse)
                .toList();

        return VehicleInspectionHistoryResponse.builder()
                .rentalUnitId(rentalUnitId)
                .items(items)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public VehicleInspectionDetailResponse getDetailByAnalysisId(Long analysisId) {
        VehicleInspectionAnalysis analysis = vehicleInspectionAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("VehicleInspectionAnalysis", analysisId));
        return toDetailResponse(analysis);
    }

    private VehicleInspectionDetailResponse toDetailResponse(VehicleInspectionAnalysis analysis) {
        VehicleInspectionNormalizedResult normalized = parseNormalized(analysis.getNormalizedResultJson());
        VehicleInspectionComparisonResult comparison = parseComparisonIfReturn(
                analysis.getStage(),
                analysis.getComparisonResultJson()
        );

        return VehicleInspectionDetailResponse.builder()
                .analysisId(analysis.getId())
                .bookingId(analysis.getBookingId())
                .rentalUnitId(analysis.getRentalUnitId())
                .handoverProtocolId(analysis.getHandoverProtocolId())
                .stage(analysis.getStage())
                .analysisStatus(analysis.getAnalysisStatus())
                .provider(analysis.getProvider())
                .model(analysis.getModel())
                .createdAt(analysis.getCreatedAt())
                .inspectionAnalysis(normalized)
                .comparison(comparison)
                .errorMessage(analysis.getErrorMessage())
                .build();
    }

    private VehicleInspectionNormalizedResult parseNormalized(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return null;
        }
        try {
            return objectMapper.readValue(json, VehicleInspectionNormalizedResult.class);
        } catch (Exception ex) {
            log.warn("Không parse được normalized_result_json: {}", ex.getMessage());
            return null;
        }
    }

    private VehicleInspectionComparisonResult parseComparisonIfReturn(String stage, String json) {
        if (stage == null || !"RETURN".equalsIgnoreCase(stage)) {
            return null;
        }
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return null;
        }
        try {
            return objectMapper.readValue(json, VehicleInspectionComparisonResult.class);
        } catch (Exception ex) {
            log.warn("Không parse được comparison_result_json: {}", ex.getMessage());
            return null;
        }
    }
}