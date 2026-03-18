package com.crs.bookingservice.dto.request;

import com.crs.bookingservice.enums.VehiclePhotoCorner;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HandoverVehiclePhotoRequest {

    @NotNull(message = "corner must not be null")
    @Schema(example = "FRONT_LEFT", allowableValues = {"FRONT_LEFT", "FRONT_RIGHT", "REAR_LEFT", "REAR_RIGHT"})
    VehiclePhotoCorner corner;

    @NotBlank(message = "imageUrl must not be blank")
    @Pattern(
            regexp = "^(https?://).+",
            message = "imageUrl must be a valid URL starting with http:// or https://"
    )
    @Schema(example = "https://cdn.example.com/rental-101-fl-pickup.jpg")
    String imageUrl;
}
