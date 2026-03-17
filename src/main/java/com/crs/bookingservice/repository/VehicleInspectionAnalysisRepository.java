package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.VehicleInspectionAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VehicleInspectionAnalysisRepository extends JpaRepository<VehicleInspectionAnalysis, Long> {

    List<VehicleInspectionAnalysis> findByRentalUnitIdOrderByCreatedAtDesc(Long rentalUnitId);

    Optional<VehicleInspectionAnalysis> findFirstByRentalUnitIdAndStageOrderByCreatedAtDesc(
            Long rentalUnitId,
            String stage
    );

    Optional<VehicleInspectionAnalysis> findFirstByRentalUnitIdAndStageAndAnalysisStatusOrderByCreatedAtDesc(
            Long rentalUnitId,
            String stage,
            String analysisStatus
    );
    Optional<VehicleInspectionAnalysis> findFirstByRentalUnitIdOrderByCreatedAtDesc(Long rentalUnitId);
}