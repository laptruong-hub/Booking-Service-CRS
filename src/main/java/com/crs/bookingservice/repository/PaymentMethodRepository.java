package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.PaymentMethod;
import com.crs.bookingservice.enums.PaymentMethodType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    Optional<PaymentMethod> findByMethodType(PaymentMethodType methodType);
}
