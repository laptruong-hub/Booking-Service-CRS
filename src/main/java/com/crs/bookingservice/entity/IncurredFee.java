package com.crs.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import com.crs.bookingservice.enums.FeeApprovalStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * INCURRED_FEE — Chi phí phát sinh thực tế trong quá trình thuê xe.
 * Liên kết với RentalUnit và SurchargePolicy (chính sách phụ phí áp dụng)
 */
@Entity
@Table(name = "incurred_fee", indexes = {
        @Index(name = "idx_incurred_rental_unit", columnList = "rental_unit_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IncurredFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_unit_id", nullable = false)
    RentalUnit rentalUnit;

    /**
     * Chính sách phụ phí được áp dụng để tính phí này
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "surcharge_policy_id")
    SurchargePolicy surchargePolicy;

    /**
     * Số tiền thực tế phát sinh
     */
    @Column(name = "total_fee", nullable = false, precision = 12, scale = 2)
    BigDecimal totalFee;

    /**
     * Mô tả phát sinh (ghi chú thêm)
     */
    @Column(length = 500)
    String description;

    @Column(name = "is_ai_suggested", nullable = false)
    @Builder.Default
    boolean aiSuggested = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    @Builder.Default
    FeeApprovalStatus approvalStatus = FeeApprovalStatus.PENDING;

    @Column(name = "ai_analysis_id")
    Long aiAnalysisId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
}
