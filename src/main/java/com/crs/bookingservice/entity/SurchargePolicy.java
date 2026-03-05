package com.crs.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * SURCHARGE_POLICY — Chính sách phụ phí áp dụng cho một RentalUnit.
 * Ví dụ: phụ phí trả trễ, phụ phí vượt km, phụ phí vệ sinh...
 */
@Entity
@Table(name = "surcharge_policy")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SurchargePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_unit_id", nullable = false)
    RentalUnit rentalUnit;

    /**
     * Tên/mã của loại phụ phí (ví dụ: LATE_RETURN, OVER_KM, CLEANING)
     */
    @Column(nullable = false, length = 100)
    String code;

    /**
     * Số tiền phụ phí
     */
    @Column(name = "fee_amount", nullable = false, precision = 12, scale = 2)
    BigDecimal feeAmount;
}
