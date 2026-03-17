package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.IncurredFee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncurredFeeRepository extends JpaRepository<IncurredFee, Long> {

    List<IncurredFee> findByRentalUnitId(Long rentalUnitId);
    
    boolean existsByAiAnalysisId(Long aiAnalysisId);

    List<IncurredFee> findByRentalUnitIdAndApprovalStatus(
            Long rentalUnitId,
            com.crs.bookingservice.enums.FeeApprovalStatus approvalStatus
    );
}
