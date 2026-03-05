package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.DriverStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * DRIVER_PROFILE — Hồ sơ tài xế, tham chiếu đến user_id từ iam-service
 * Mỗi tài xế có thể lái nhiều RentalUnit (xe) trong các booking khác nhau
 */
@Entity
@Table(name = "driver_profile", indexes = {
        @Index(name = "idx_driver_user_id", columnList = "user_id"),
        @Index(name = "idx_driver_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DriverProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    /**
     * user_id tham chiếu từ iam-service (không FK vật lý do cross-service)
     */
    @Column(name = "user_id", nullable = false, unique = true, length = 36)
    String userId;

    @Column(name = "license_number", nullable = false, unique = true, length = 30)
    String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    DriverStatus status = DriverStatus.ACTIVE;

    /**
     * Vị trí hiện tại của tài xế (tên địa điểm hoặc toạ độ text)
     */
    @Column(name = "current_location", length = 255)
    String currentLocation;

    @Column(name = "average_rating", columnDefinition = "NUMERIC(3,2)")
    Double averageRating;

    @UpdateTimestamp
    @Column(name = "updated_at")
    LocalDateTime updatedAt;
}
