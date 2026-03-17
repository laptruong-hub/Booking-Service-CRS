package com.crs.bookingservice.dto.request;
import com.crs.bookingservice.enums.VehiclePhotoCorner;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HandoverVehiclePhotoRequest {

    @NotNull(message = "corner không được để trống")
    VehiclePhotoCorner corner;

    @NotBlank(message = "imageUrl không được để trống")
    @Pattern(
        regexp = "^(https?://).+",
        message = "imageUrl phải là URL hợp lệ bắt đầu bằng http:// hoặc https://"
    )
    String imageUrl;
}