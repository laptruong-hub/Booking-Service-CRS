package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.dto.ai.VehicleInspectionComparisonResult;
import com.crs.bookingservice.dto.ai.VehicleInspectionNormalizedResult;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VehicleInspectionDetailResponse {

    Long analysisId;
    Long bookingId;
    Long rentalUnitId;
    Long handoverProtocolId;
    String stage;
    String analysisStatus;
    String provider;
    String model;
    LocalDateTime createdAt;
    VehicleInspectionNormalizedResult inspectionAnalysis;
    VehicleInspectionComparisonResult comparison;
    String errorMessage;
}