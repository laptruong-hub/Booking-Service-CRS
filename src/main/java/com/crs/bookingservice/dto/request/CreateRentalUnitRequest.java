package com.crs.bookingservice.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Request thêm một xe vào booking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRentalUnitRequest {

    @NotNull(message = "vehicleId không được để trống")
    Long vehicleId;

    Long driverId; // null nếu khách tự lái

    @Builder.Default
    Boolean isWithDriver = false;

    @NotNull(message = "startTime không được để trống")
    LocalDateTime startTime;

    @NotNull(message = "endTime không được để trống")
    @Future(message = "endTime phải là thời điểm trong tương lai")
    LocalDateTime endTime;

    @NotNull(message = "unitPrice không được để trống")
    BigDecimal unitPrice;
}
