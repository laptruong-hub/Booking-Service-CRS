package com.crs.bookingservice.dto.request;

import com.crs.bookingservice.enums.DeliveryMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Request tạo booking mới
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateRentalGroupRequest {

    @NotBlank(message = "userId không được để trống")
    String userId;

    @NotNull(message = "deliveryMode không được để trống")
    DeliveryMode deliveryMode;

    String deliveryAddress; // Bắt buộc nếu deliveryMode = DELIVERY

    @NotEmpty(message = "Phải có ít nhất 1 xe trong booking")
    @Valid
    List<CreateRentalUnitRequest> rentalUnits;
}
