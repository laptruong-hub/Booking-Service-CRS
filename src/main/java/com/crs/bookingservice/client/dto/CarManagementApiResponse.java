package com.crs.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wrapper ApiResponse<T> của car-management-service.
 * Dùng để deserialize response từ car-management API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CarManagementApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}
