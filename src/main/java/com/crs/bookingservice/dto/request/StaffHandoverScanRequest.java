package com.crs.bookingservice.dto.request;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class StaffHandoverScanRequest {

    @NotNull(message = "rentalUnitId must not be null")
    @Schema(example = "101")
    Long rentalUnitId;

    @NotNull(message = "vehiclePhotos must not be null")
    @Size(min = 4, max = 4, message = "Scan AI requires exactly 4 vehicle photos")
    @Valid
    @ArraySchema(minItems = 4, maxItems = 4, schema = @Schema(implementation = HandoverVehiclePhotoRequest.class))
    List<HandoverVehiclePhotoRequest> vehiclePhotos;
}
