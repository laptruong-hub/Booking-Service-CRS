package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.InspectionSeverity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vehicle_inspection_analysis", indexes = {
        @Index(name = "idx_via_booking", columnList = "booking_id"),
        @Index(name = "idx_via_rental_unit", columnList = "rental_unit_id"),
        @Index(name = "idx_via_stage", columnList = "stage"),
        @Index(name = "idx_via_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VehicleInspectionAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(name = "booking_id", nullable = false)
    Long bookingId;

    @Column(name = "rental_unit_id", nullable = false)
    Long rentalUnitId;

    @Column(name = "handover_protocol_id")
    Long handoverProtocolId;

    @Column(name = "baseline_analysis_id")
    Long baselineAnalysisId;

    @Column(name = "comparison_result_json", columnDefinition = "TEXT")
    String comparisonResultJson;

    @Column(nullable = false, length = 20)
    String stage; // PICKUP | RETURN

    @Column(nullable = false, length = 50)
    String provider; // GEMINI

    @Column(nullable = false, length = 100)
    String model; // gemini-1.5-flash

    @Column(name = "analysis_status", nullable = false, length = 50)
    String analysisStatus; // SUCCESS, RATE_LIMIT, TIMEOUT, ...

    @Column(name = "damage_detected", nullable = false)
    boolean damageDetected;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    InspectionSeverity severity;

    @Column(precision = 5, scale = 4)
    BigDecimal confidence;

    @Column(name = "needs_manual_review", nullable = false)
    boolean needsManualReview;

    @Column(name = "recommended_fee", precision = 12, scale = 2)
    BigDecimal recommendedFee;

    @Column(length = 2000)
    String summary;

    @Column(name = "damages_json", columnDefinition = "TEXT")
    String damagesJson;

    @Column(name = "normalized_result_json", columnDefinition = "TEXT")
    String normalizedResultJson;

    @Column(name = "raw_response_json", columnDefinition = "TEXT")
    String rawResponseJson;

    @Column(name = "error_message", length = 1000)
    String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
}