package com.crs.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

import java.math.BigDecimal;

/**
 * Tổng quan dashboard cho khoảng thời gian đã chọn (7 ngày / 30 ngày).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DashboardOverviewResponse {

    /** Tổng doanh thu trong kỳ (không tính CANCELLED) */
    BigDecimal totalRevenue;

    /** Tổng chuyến xe trong kỳ (không tính CANCELLED) */
    long totalTrips;

    /** % thay đổi doanh thu so với kỳ trước (có thể âm) */
    double revenueChangePercent;

    /** % thay đổi số chuyến so với kỳ trước (có thể âm) */
    double tripsChangePercent;

    /** Số booking đang chờ duyệt (PENDING) */
    long pendingBookings;
}
