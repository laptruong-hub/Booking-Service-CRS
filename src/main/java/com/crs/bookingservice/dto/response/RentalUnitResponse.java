package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.enums.RentalUnitStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;
import com.crs.bookingservice.enums.InspectionSeverity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response trả về chi tiết một xe trong booking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RentalUnitResponse {

    Long id;
    Long vehicleId;
    Long driverId;
    Boolean isWithDriver;

    // Enrich từ car-management-service
    String vehiclePlateNumber;
    String vehicleBrand;
    String vehicleModel;
    String vehicleStatus;

    // Enrich từ iam-service (thông tin tài xế)
    String driverName;
    String driverPhone;

    LocalDateTime startTime;
    LocalDateTime endTime;
    LocalDateTime actualReturnTime;
    BigDecimal unitPrice;
    BigDecimal faultPercent;
    RentalUnitStatus status;
    // ── AI Inspection (Step 9 - Staff App) ──────────────────────────
    Long inspectionAnalysisId;       // ID của bản phân tích gần nhất
    String inspectionStage;          // PICKUP | RETURN
    String inspectionStatus;         // SUCCESS | TIMEOUT | RATE_LIMIT | ...
    InspectionSeverity inspectionSeverity;
    String comparisonSummary;        // summary so sánh PICKUP vs RETURN (RETURN stage)
    BigDecimal inspectionRecommendedFee;
    Boolean needsManualReview;
    Boolean newDamageDetected;       // chỉ có giá trị ở RETURN stage
}
