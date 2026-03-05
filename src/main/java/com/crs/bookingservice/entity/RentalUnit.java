package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.RentalUnitStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RENTAL_UNIT — Một xe đơn lẻ trong một booking (RentalGroup).
 * Tham chiếu vehicle_id từ car-management-service.
 * Tham chiếu driver_id từ DriverProfile trong booking-service.
 */
@Entity
@Table(name = "rental_unit", indexes = {
        @Index(name = "idx_ru_rental_group", columnList = "rental_group_id"),
        @Index(name = "idx_ru_vehicle_id", columnList = "vehicle_id"),
        @Index(name = "idx_ru_driver_id", columnList = "driver_id"),
        @Index(name = "idx_ru_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RentalUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_group_id", nullable = false)
    RentalGroup rentalGroup;

    /**
     * vehicle_id tham chiếu từ car-management-service (không FK vật lý)
     */
    @Column(name = "vehicle_id", nullable = false)
    Long vehicleId;

    /**
     * Tài xế được chỉ định lái xe này
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    DriverProfile driver;

    /**
     * Có tài xế đi kèm hay không (khách tự lái)
     */
    @Column(name = "is_with_driver", nullable = false)
    @Builder.Default
    Boolean isWithDriver = false;

    @Column(name = "start_time", nullable = false)
    LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    LocalDateTime endTime;

    /**
     * Thời điểm thực tế trả xe
     */
    @Column(name = "actual_return_time")
    LocalDateTime actualReturnTime;

    /**
     * Đơn giá thuê xe (VND/ngày hoặc VND/giờ tuỳ config)
     */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    BigDecimal unitPrice;

    /**
     * Phí phát sinh bổ sung (late fee, damage fee, ...)
     */
    @Column(name = "fault_percent", precision = 5, scale = 2)
    @Builder.Default
    BigDecimal faultPercent = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    RentalUnitStatus status = RentalUnitStatus.PENDING;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    // ============ Relationships ============

    @OneToMany(mappedBy = "rentalUnit", cascade = CascadeType.ALL)
    @Builder.Default
    List<SurchargePolicy> surchargePolicies = new ArrayList<>();

    @OneToMany(mappedBy = "rentalUnit", cascade = CascadeType.ALL)
    @Builder.Default
    List<IncurredFee> incurredFees = new ArrayList<>();

    @OneToOne(mappedBy = "rentalUnit", cascade = CascadeType.ALL)
    HandoverProtocol handoverProtocol;

    @OneToMany(mappedBy = "rentalUnit", cascade = CascadeType.ALL)
    @Builder.Default
    List<DriverFeedback> feedbacks = new ArrayList<>();
}
