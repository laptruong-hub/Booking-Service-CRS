package com.crs.bookingservice.service;

import com.crs.bookingservice.dto.request.HandoverVehiclePhotoRequest;
import com.crs.bookingservice.dto.response.HandoverAiPreviewResponse;

import java.util.List;

public interface VehicleInspectionAiService {

    /**
     * Gọi AI theo cơ chế best-effort:
     * - Không throw exception ra ngoài
     * - Không làm fail workflow giao/nhận xe
     */
    void analyzeHandoverSafely(
            Long bookingId,
            Long rentalUnitId,
            String stage,
            List<HandoverVehiclePhotoRequest> vehiclePhotos
    );

    /**
     * Scan AI trước khi staff xác nhận bàn giao/nhận xe.
     * Không thay đổi trạng thái booking/rental unit, nhưng có persist analysis record để dùng lại khi confirm.
     */
    HandoverAiPreviewResponse scanHandover(
            Long bookingId,
            Long rentalUnitId,
            String stage,
            List<HandoverVehiclePhotoRequest> vehiclePhotos
    );
}