package com.crs.bookingservice.service;

import com.crs.bookingservice.dto.response.VehicleInspectionDetailResponse;
import com.crs.bookingservice.dto.response.VehicleInspectionHistoryResponse;

public interface VehicleInspectionQueryService {

    VehicleInspectionHistoryResponse getHistoryByRentalUnit(Long rentalUnitId, String stage);

    VehicleInspectionDetailResponse getDetailByAnalysisId(Long analysisId);
}