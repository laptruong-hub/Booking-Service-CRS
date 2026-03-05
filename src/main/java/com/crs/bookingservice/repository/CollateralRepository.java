package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.Collateral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollateralRepository extends JpaRepository<Collateral, Long> {

    Optional<Collateral> findByRentalGroupId(Long rentalGroupId);
}
