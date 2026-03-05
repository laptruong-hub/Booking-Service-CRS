package com.crs.bookingservice.client;

import com.crs.bookingservice.client.dto.CarManagementApiResponse;
import com.crs.bookingservice.client.dto.VehicleDto;
import com.crs.bookingservice.client.dto.VehiclePageDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign client giao tiếp với car-management-service.
 * Car-management không có Spring Security → gọi trực tiếp.
 * Tất cả endpoints đều trả ApiResponse<T> wrapper.
 */
@FeignClient(name = "car-management-service", url = "${feign.clients.car-management-service.url}")
public interface CarManagementClient {

    /**
     * Lấy chi tiết một xe theo ID.
     * → GET /api/v1/vehicles/{id}
     * Response: ApiResponse<VehicleDetailResponse>
     */
    @GetMapping("/api/v1/vehicles/{id}")
    CarManagementApiResponse<VehicleDto> getVehicleById(@PathVariable("id") Long vehicleId);

    /**
     * Lấy danh sách xe phân trang.
     * → GET /api/v1/vehicles?page=0&size=20
     */
    @GetMapping("/api/v1/vehicles")
    CarManagementApiResponse<VehiclePageDto> getAllVehicles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir);

    /**
     * Lấy danh sách xe theo status.
     * → GET /api/v1/vehicles/status/{status}
     * Dùng để lấy xe AVAILABLE khi khách tìm kiếm.
     */
    @GetMapping("/api/v1/vehicles/status/{status}")
    CarManagementApiResponse<List<VehicleDto>> getVehiclesByStatus(
            @PathVariable("status") String status);
}
