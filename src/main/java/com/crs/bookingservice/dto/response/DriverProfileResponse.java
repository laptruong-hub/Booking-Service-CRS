package com.crs.bookingservice.dto.response;

import com.crs.bookingservice.enums.DriverStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Response trả về thông tin tài xế
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DriverProfileResponse {

    Long id;
    String userId;
    String licenseNumber;
    DriverStatus status;
    String currentLocation;
    Double averageRating;

    // Enrich từ iam-service
    String fullName;
    String email;
    String phone;
}
