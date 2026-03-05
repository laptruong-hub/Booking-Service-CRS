package com.crs.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * HANDOVER_PROTOCOL — Biên bản bàn giao xe (nhận xe / trả xe).
 * Gắn với một RentalUnit cụ thể.
 */
@Entity
@Table(name = "handover_protocol", uniqueConstraints = {
        // Ràng buộc: Một RentalUnit chỉ có duy nhất 1 bản PICKUP và 1 bản RETURN
        @UniqueConstraint(columnNames = { "rental_unit_id", "type" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HandoverProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_unit_id", nullable = false)
    RentalUnit rentalUnit;

    /**
     * Loại biên bản: PICKUP (nhận xe) / RETURN (trả xe)
     */
    @Column(nullable = false, length = 20)
    String type;

    /**
     * Số km đồng hồ tại thời điểm bàn giao
     */
    @Column(name = "odo_meter")
    Double odoMeter;

    /**
     * Trạng thái xe tại thời điểm bàn giao (mô tả text)
     */
    @Column(length = 1000)
    String condition;

    /**
     * Danh sách URL ảnh chụp biên bản (JSON array dạng text)
     */
    @Column(columnDefinition = "TEXT")
    String photos;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
}
