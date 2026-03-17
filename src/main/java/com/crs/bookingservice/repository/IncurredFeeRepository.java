package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.IncurredFee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IncurredFeeRepository extends JpaRepository<IncurredFee, Long> {

    List<IncurredFee> findByRentalUnitId(Long rentalUnitId);

    Optional<IncurredFee> findByAiAnalysisId(Long aiAnalysisId);
    
    boolean existsByAiAnalysisId(Long aiAnalysisId);

        boolean existsByRentalUnitIdAndApprovalStatusAndAiSuggestedTrue(
            Long rentalUnitId,
            com.crs.bookingservice.enums.FeeApprovalStatus approvalStatus
        );

    List<IncurredFee> findByRentalUnitIdAndApprovalStatus(
            Long rentalUnitId,
            com.crs.bookingservice.enums.FeeApprovalStatus approvalStatus
    );
}
