package com.crs.bookingservice.repository;

import com.crs.bookingservice.entity.Invoice;
import com.crs.bookingservice.enums.InvoiceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByRentalGroupId(Long rentalGroupId);

    List<Invoice> findByRentalGroupIdAndType(Long rentalGroupId, InvoiceType type);
}
