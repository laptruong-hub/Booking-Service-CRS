package com.crs.bookingservice.service;

import com.crs.bookingservice.dto.request.HandoverVehiclePhotoRequest;

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
}