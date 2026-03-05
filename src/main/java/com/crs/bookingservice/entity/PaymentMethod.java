package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.PaymentMethodType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * PAYMENT_METHOD — Danh mục phương thức thanh toán hỗ trợ
 */
@Entity
@Table(name = "payment_method")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, unique = true, length = 30)
    PaymentMethodType methodType;

    /**
     * Tên hiển thị (ví dụ: "Tiền mặt", "Chuyển khoản ngân hàng")
     */
    @Column(name = "display_name", nullable = false, length = 100)
    String displayName;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    Boolean isActive = true;
}
