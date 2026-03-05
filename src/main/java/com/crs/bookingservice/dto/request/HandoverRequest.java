package com.crs.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Ghi nhận biên bản bàn giao xe (nhận hoặc trả xe).
 * type = "PICKUP" khi bắt đầu chuyến, "RETURN" khi kết thúc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HandoverRequest {

    @NotNull(message = "rentalUnitId không được để trống")
    Long rentalUnitId;

    /**
     * Loại bàn giao: PICKUP (giao xe cho khách) hoặc RETURN (nhận xe lại)
     */
    @NotBlank(message = "type không được để trống (PICKUP / RETURN)")
    String type;

    /**
     * Số km đồng hồ tại thời điểm bàn giao
     */
    @NotNull(message = "odoMeter không được để trống")
    Double odoMeter;

    /**
     * Mô tả trạng thái xe (vết xước, lốp, đèn, ...)
     */
    String condition;

    /**
     * Danh sách URL ảnh chụp (gửi dưới dạng JSON string array hoặc comma-separated)
     */
    String photos;
}
