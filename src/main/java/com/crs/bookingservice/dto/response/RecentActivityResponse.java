package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Hoạt động gần nhất hiển thị trong dashboard (mỗi booking = 1 dòng).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecentActivityResponse {

    Long id;
    String bookingCode;
    String userId;
    String customerName;
    BookingStatus status;
    BigDecimal totalAmount;
    LocalDateTime createdAt;
}
