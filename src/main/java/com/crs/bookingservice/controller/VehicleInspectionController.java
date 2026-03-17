package com.crs.bookingservice.controller;

import com.crs.bookingservice.dto.response.ApiResponse;
import com.crs.bookingservice.dto.response.VehicleInspectionDetailResponse;
import com.crs.bookingservice.dto.response.VehicleInspectionHistoryResponse;
import com.crs.bookingservice.service.VehicleInspectionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vehicle-inspections")
@RequiredArgsConstructor
@Tag(name = "Vehicle Inspection", description = "API truy vấn dữ liệu giám định AI")
public class VehicleInspectionController {

    private final VehicleInspectionQueryService vehicleInspectionQueryService;

    @GetMapping("/rental-unit/{rentalUnitId}")
    @Operation(summary = "Lấy lịch sử giám định theo rentalUnitId", description = "Có thể filter stage=PICKUP hoặc stage=RETURN")
    public ResponseEntity<ApiResponse<VehicleInspectionHistoryResponse>> getHistoryByRentalUnit(
            @PathVariable Long rentalUnitId,
            @RequestParam(required = false) String stage
    ) {
        VehicleInspectionHistoryResponse data = vehicleInspectionQueryService
                .getHistoryByRentalUnit(rentalUnitId, stage);
        return ResponseEntity.ok(ApiResponse.success(data, "OK"));
    }

    @GetMapping("/{analysisId}")
    @Operation(summary = "Lấy chi tiết một bản ghi giám định", description = "PICKUP sẽ không có comparison, RETURN mới có comparison")
    public ResponseEntity<ApiResponse<VehicleInspectionDetailResponse>> getDetailByAnalysisId(
            @PathVariable Long analysisId
    ) {
        VehicleInspectionDetailResponse data = vehicleInspectionQueryService.getDetailByAnalysisId(analysisId);
        return ResponseEntity.ok(ApiResponse.success(data, "OK"));
    }
}