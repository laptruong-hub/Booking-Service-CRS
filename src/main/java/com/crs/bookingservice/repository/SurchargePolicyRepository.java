package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.SurchargePolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurchargePolicyRepository extends JpaRepository<SurchargePolicy, Long> {

    List<SurchargePolicy> findByRentalUnitId(Long rentalUnitId);
}
