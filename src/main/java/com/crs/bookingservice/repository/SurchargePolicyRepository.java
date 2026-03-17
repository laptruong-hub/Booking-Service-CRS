package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.SurchargePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SurchargePolicyRepository extends JpaRepository<SurchargePolicy, Long> {

    List<SurchargePolicy> findByRentalUnitId(Long rentalUnitId);

    // Tra cứu policy riêng của một xe theo code
    Optional<SurchargePolicy> findFirstByRentalUnitIdAndCodeIgnoreCaseOrderByIdAsc(
            Long rentalUnitId,
            String code
    );

    Optional<SurchargePolicy> findFirstByRentalUnitIdOrderByIdAsc(Long rentalUnitId);

    // Tra cứu global template (rental_unit_id = NULL) theo code
    Optional<SurchargePolicy> findFirstByRentalUnitIsNullAndCodeIgnoreCaseOrderByIdAsc(String code);
}
