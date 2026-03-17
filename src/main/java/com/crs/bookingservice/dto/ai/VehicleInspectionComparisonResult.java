package com.crs.bookingservice.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleInspectionComparisonResult {

    Boolean baselineFound;
    Long baselineAnalysisId;
    LocalDateTime baselineCreatedAt;

    Integer baselineDamageCount;
    Integer returnDamageCount;
    Integer newDamageCount;

    Boolean newDamageDetected;
    List<VehicleInspectionNormalizedResult.DamageItem> newDamages;
    String summary;
}