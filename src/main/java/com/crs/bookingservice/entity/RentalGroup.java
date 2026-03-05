package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.BookingStatus;
import com.crs.bookingservice.enums.DeliveryMode;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RENTAL_GROUP — Đơn đặt xe (booking).
 * Một booking có thể gồm nhiều xe (RentalUnit).
 * Tham chiếu user_id từ iam-service.
 */
@Entity
@Table(name = "rental_group", indexes = {
        @Index(name = "idx_rg_user_id", columnList = "user_id"),
        @Index(name = "idx_rg_booking_code", columnList = "booking_code"),
        @Index(name = "idx_rg_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RentalGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /**
     * user_id của khách hàng đặt xe, tham chiếu từ iam-service
     */
    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    /**
     * Mã booking duy nhất (ví dụ: BK-20260305-001)
     */
    @Column(name = "booking_code", nullable = false, unique = true, length = 50)
    String bookingCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 20)
    @Builder.Default
    DeliveryMode deliveryMode = DeliveryMode.SELF_PICKUP;

    /**
     * Địa chỉ giao xe (nếu delivery_mode = DELIVERY)
     */
    @Column(name = "delivery_address", length = 500)
    String deliveryAddress;

    /**
     * Phí giao xe
     */
    @Column(name = "delivery_fee", precision = 12, scale = 2)
    @Builder.Default
    BigDecimal deliveryFee = BigDecimal.ZERO;

    /**
     * Tổng tiền của toàn bộ booking (tổng tất cả RentalUnit)
     */
    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Tiền đặt cọc yêu cầu
     */
    @Column(name = "deposit_required", precision = 12, scale = 2)
    @Builder.Default
    BigDecimal depositRequired = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    BookingStatus status = BookingStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    // ============ Relationships ============

    @OneToMany(mappedBy = "rentalGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<RentalUnit> rentalUnits = new ArrayList<>();

    @OneToMany(mappedBy = "rentalGroup", cascade = CascadeType.ALL)
    @Builder.Default
    List<Invoice> invoices = new ArrayList<>();

    @OneToOne(mappedBy = "rentalGroup", cascade = CascadeType.ALL)
    Contract contract;

    @OneToOne(mappedBy = "rentalGroup", cascade = CascadeType.ALL)
    Collateral collateral;
}
