package com.crs.bookingservice.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mirror của VehicleResponse từ car-management.
 *
 * VehicleResponse thực tế trả về field `model` dạng nested object (id, name,
 * brand).
 * → Phải dùng ModelInfo class thay vì flat fields.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) bắt buộc vì VehicleDetailResponse
 *                                     có nhiều field hơn VehicleResponse.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehicleDto {

    Long id;
    String plateNumber;
    String color;
    String status; // VehicleStatus enum serialize thành String (AVAILABLE, RENTED...)
    Boolean isVirtual;
    Double odometerKm;

    // ⚠️ VehicleResponse có nested object "model", không phải flat fields
    ModelInfo model;

    // VehicleDetailResponse có thêm các field flat (không cần thiết nếu dùng model)
    String fleetHubName;
    String fleetHubLocation;

    // State info (real-time telemetry)
    StateInfo currentState;

    LocalDateTime createdAt;

    // ==============================================================
    // Helper methods để lấy flat values (dùng trong service/mapper)
    // ==============================================================

    public String getModelName() {
        return model != null ? model.getName() : null;
    }

    public String getBrand() {
        return model != null ? model.getBrand() : null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelInfo {
        Long id;
        String name;
        String brand;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StateInfo {
        Double latitude;
        Double longitude;
        Integer batteryLevel;
        Boolean isCharging;
        Double speedKmh;
        LocalDateTime lastUpdatedAt;
    }
}
