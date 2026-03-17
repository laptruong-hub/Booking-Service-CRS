package com.crs.bookingservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.crs.bookingservice.enums.VehiclePhotoCorner;
import jakarta.validation.Valid;

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
    @NotNull(message = "vehiclePhotos không được để trống")
    @Size(min = 4, max = 4, message = "Phải cung cấp đúng 4 ảnh xe")
    @Valid
    List<HandoverVehiclePhotoRequest> vehiclePhotos;

    @AssertTrue(message = "Phải có đủ 4 góc: FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT và không được trùng")
    public boolean isVehiclePhotosValid() {
        if (vehiclePhotos == null || vehiclePhotos.size() != 4) {
            return false;
        }

        Set<VehiclePhotoCorner> corners = vehiclePhotos.stream()
                .map(HandoverVehiclePhotoRequest::getCorner)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return corners.equals(EnumSet.allOf(VehiclePhotoCorner.class));
    }
}
