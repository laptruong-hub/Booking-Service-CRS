package com.crs.bookingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

/**
 * Một điểm dữ liệu trên biểu đồ doanh thu / đặt xe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChartDataPointResponse {

    /** Nhãn trục X: tên ngày (T2–CN) hoặc tên tuần (Tuần 1–4) */
    String name;

    /** Doanh thu (VND) */
    long revenue;

    /** Số booking */
    int bookings;
}
