package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.enums.InvoiceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceResponse {

    Long id;
    InvoiceType type;
    BigDecimal amount;

    /** null nếu chưa thanh toán */
    LocalDateTime paidAt;

    /** Tên phương thức thanh toán đã dùng (null nếu chưa trả) */
    String paymentMethodType;

    /** "PAID" | "UNPAID" */
    String status;
}
