package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.enums.BookingStatus;
import com.crs.bookingservice.enums.DeliveryMode;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response trả về chi tiết một booking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RentalGroupResponse {

    Long id;
    String userId;
    String bookingCode;

    // Enrich từ iam-service
    String customerName;
    String customerEmail;
    String customerPhone;

    DeliveryMode deliveryMode;
    String deliveryAddress;
    BigDecimal deliveryFee;
    BigDecimal totalAmount;
    BigDecimal depositRequired;
    BookingStatus status;
    LocalDateTime createdAt;
    List<RentalUnitResponse> rentalUnits;
}
