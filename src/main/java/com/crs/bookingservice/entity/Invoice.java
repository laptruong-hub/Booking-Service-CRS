package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.InvoiceType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * INVOICE — Hoá đơn phát sinh cho một booking.
 * Một booking có thể có nhiều hoá đơn (đặt cọc, tiền thuê, phát sinh, hoàn
 * tiền)
 */
@Entity
@Table(name = "invoice", indexes = {
        @Index(name = "idx_invoice_rental_group", columnList = "rental_group_id"),
        @Index(name = "idx_invoice_type", columnList = "type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_group_id", nullable = false)
    RentalGroup rentalGroup;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    InvoiceType type;

    /**
     * Phương thức thanh toán áp dụng cho hoá đơn này
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    PaymentMethod paymentMethod;

    /**
     * Thời gian thanh toán (nếu đã thanh toán)
     */
    @Column(name = "paid_at")
    LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
}
