package com.crs.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * DRIVER_FEEDBACK — Đánh giá tài xế sau chuyến đi.
 * Một RentalUnit có thể có nhiều feedback (khách đánh giá tài xế)
 */
@Entity
@Table(name = "driver_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DriverFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_unit_id", nullable = false)
    RentalUnit rentalUnit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    DriverProfile driver;

    /**
     * Điểm đánh giá tài xế (1-5 sao)
     */
    @Column(nullable = false)
    Integer rating;

    /**
     * Nhận xét chi tiết
     */
    @Column(length = 1000)
    String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
}
