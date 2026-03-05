package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.CollateralStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * COLLATERAL — Tài sản thế chấp của một booking (ảnh CCCD, xe máy,...)
 */
@Entity
@Table(name = "collateral")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Collateral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_group_id", nullable = false, unique = true)
    RentalGroup rentalGroup;

    /**
     * Danh sách URL ảnh tài sản thế chấp (JSON array as text)
     */
    @Column(name = "image_urls", columnDefinition = "TEXT")
    String imageUrls;

    /**
     * Giá trị ước tính của tài sản thế chấp
     */
    @Column(precision = 12, scale = 2)
    BigDecimal value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    CollateralStatus status = CollateralStatus.HELD;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
}
