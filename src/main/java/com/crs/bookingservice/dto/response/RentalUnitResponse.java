package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.enums.RentalUnitStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response trả về chi tiết một xe trong booking
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RentalUnitResponse {

    Long id;
    Long vehicleId;
    Long driverId;
    Boolean isWithDriver;

    // Enrich từ car-management-service
    String vehiclePlateNumber;
    String vehicleBrand;
    String vehicleModel;
    String vehicleStatus;

    // Enrich từ iam-service (thông tin tài xế)
    String driverName;
    String driverPhone;

    LocalDateTime startTime;
    LocalDateTime endTime;
    LocalDateTime actualReturnTime;
    BigDecimal unitPrice;
    BigDecimal faultPercent;
    RentalUnitStatus status;
}
