package com.crs.bookingservice.controller;

import com.crs.bookingservice.client.CarManagementClient;
import com.crs.bookingservice.client.dto.VehicleDto;
import com.crs.bookingservice.client.dto.VehiclePageDto;
import com.crs.bookingservice.dto.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * VehicleController — proxy API lấy thông tin xe từ car-management-service qua
 * OpenFeign.
 * Booking-service expose các endpoints này để frontend chỉ cần gọi một điểm duy
 * nhất (qua API Gateway).
 */
@RestController
@RequestMapping("/api/v1/vehicles")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Vehicle (via car-management)", description = "Lấy thông tin xe từ car-management-service qua OpenFeign")
public class VehicleProxyController {

    private final CarManagementClient carManagementClient;

    @GetMapping
    @Operation(summary = "Lấy danh sách xe (phân trang)", description = """
            Proxy đến car-management-service để lấy toàn bộ xe, có phân trang.
            Dùng khi khách hàng xem trang tìm xe.
            """)
    public ResponseEntity<ApiResponse<VehiclePageDto>> getAllVehicles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir) {

        log.debug("[Feign] Lấy danh sách xe — page={}, size={}", page, size);
        var result = carManagementClient.getAllVehicles(page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), "OK"));
    }

    @GetMapping("/available")
    @Operation(summary = "Lấy danh sách xe đang AVAILABLE", description = """
            Proxy đến car-management-service lọc xe theo status=AVAILABLE.
            **Đây là endpoint chính để khách hàng tìm xe để đặt.**
            """)
    public ResponseEntity<ApiResponse<List<VehicleDto>>> getAvailableVehicles() {
        log.debug("[Feign] Lấy danh sách xe AVAILABLE");
        var result = carManagementClient.getVehiclesByStatus("AVAILABLE");
        return ResponseEntity.ok(ApiResponse.success(result.getData(), "OK"));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Lấy xe theo trạng thái (AVAILABLE, RENTED, CHARGING, OFFLINE...)", description = "Proxy lọc xe theo status từ car-management. Dùng để Staff xem xe đang RENTED.")
    public ResponseEntity<ApiResponse<List<VehicleDto>>> getVehiclesByStatus(
            @PathVariable String status) {
        log.debug("[Feign] Lấy xe theo status: {}", status);
        var result = carManagementClient.getVehiclesByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), "OK"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết một xe theo ID", description = "Proxy đến car-management để lấy đầy đủ thông tin xe (model, hub, state, battery...).")
    public ResponseEntity<ApiResponse<VehicleDto>> getVehicleById(@PathVariable Long id) {
        log.debug("[Feign] Lấy chi tiết xe #{}", id);
        var result = carManagementClient.getVehicleById(id);
        return ResponseEntity.ok(ApiResponse.success(result.getData(), "OK"));
    }
}
