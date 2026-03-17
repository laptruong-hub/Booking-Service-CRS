package com.crs.bookingservice.dto.ai;

import com.crs.bookingservice.enums.InspectionSeverity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleInspectionNormalizedResult {

    Boolean damageDetected;
    List<DamageItem> damages;
    InspectionSeverity severity;
    BigDecimal confidence;
    Boolean needsManualReview;
    BigDecimal recommendedFee;
    String summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DamageItem {
        String part;
        String type;
        InspectionSeverity severity;
        BigDecimal estimatedFee;
        String note;
    }
}