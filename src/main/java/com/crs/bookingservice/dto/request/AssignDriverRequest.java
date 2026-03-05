package com.crs.bookingservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Staff gán tài xế cho một RentalUnit cụ thể trong booking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignDriverRequest {

    @NotNull(message = "rentalUnitId không được để trống")
    Long rentalUnitId;

    @NotNull(message = "driverId không được để trống")
    Long driverId;
}
