package com.crs.bookingservice.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentResponse {

    Long invoiceId;
    String status; // e.g., "PAID", "PENDING_QR"
    BigDecimal amount;
    String qrCodeData; // For QR payments
    String message;
}