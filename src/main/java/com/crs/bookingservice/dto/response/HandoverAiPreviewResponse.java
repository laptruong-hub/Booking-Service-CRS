package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.dto.ai.VehicleInspectionComparisonResult;
import com.crs.bookingservice.dto.ai.VehicleInspectionNormalizedResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HandoverAiPreviewResponse {

    Long bookingId;
    Long rentalUnitId;
    Long inspectionAnalysisId;
    String stage;
    String analysisStatus;
    VehicleInspectionNormalizedResult inspectionAnalysis;
    VehicleInspectionComparisonResult comparison;
    String errorMessage;
}