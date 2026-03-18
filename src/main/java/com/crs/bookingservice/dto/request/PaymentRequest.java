package com.crs.bookingservice.dto.request;

import com.crs.bookingservice.enums.PaymentMethodType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PaymentRequest {

    @NotNull(message = "Invoice ID is required")
    Long invoiceId;

    @NotNull(message = "Payment method type is required")
    PaymentMethodType paymentMethodType;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    BigDecimal amount;
}