package com.crs.bookingservice.service.impl;

import com.crs.bookingservice.dto.request.PaymentRequest;
import com.crs.bookingservice.dto.response.PaymentResponse;
import com.crs.bookingservice.entity.Invoice;
import com.crs.bookingservice.entity.PaymentMethod;
import com.crs.bookingservice.enums.PaymentMethodType;
import com.crs.bookingservice.exception.ResourceNotFoundException;
import com.crs.bookingservice.repository.InvoiceRepository;
import com.crs.bookingservice.repository.PaymentMethodRepository;
import com.crs.bookingservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentMethodRepository paymentMethodRepository;

    @Override
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request) {
        // Find invoice
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));

        // Check if already paid
        if (invoice.getPaidAt() != null) {
            return PaymentResponse.builder()
                    .invoiceId(invoice.getId())
                    .status("ALREADY_PAID")
                    .amount(invoice.getAmount())
                    .message("Invoice already paid")
                    .build();
        }

        // Find payment method
        PaymentMethod paymentMethod = paymentMethodRepository.findByMethodType(request.getPaymentMethodType())
                .orElseThrow(() -> new ResourceNotFoundException("Payment method not found"));

        // Validate amount
        if (request.getAmount().compareTo(invoice.getAmount()) != 0) {
            return PaymentResponse.builder()
                    .invoiceId(invoice.getId())
                    .status("AMOUNT_MISMATCH")
                    .amount(invoice.getAmount())
                    .message("Payment amount does not match invoice amount")
                    .build();
        }

        // Process payment based on type
        if (request.getPaymentMethodType() == PaymentMethodType.CASH) {
            // For cash, mark as paid immediately
            invoice.setPaymentMethod(paymentMethod);
            invoice.setPaidAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            return PaymentResponse.builder()
                    .invoiceId(invoice.getId())
                    .status("PAID")
                    .amount(invoice.getAmount())
                    .message("Payment processed successfully with cash")
                    .build();
        } else if (request.getPaymentMethodType() == PaymentMethodType.E_WALLET) {
            // For QR code (e-wallet), generate QR code data
            // In real implementation, integrate with payment gateway
            String qrCodeData = generateQrCodeData(invoice);

          // imidiately paid
            invoice.setPaymentMethod(paymentMethod);
            invoice.setPaidAt(LocalDateTime.now());
            invoiceRepository.save(invoice);

            return PaymentResponse.builder()
                    .invoiceId(invoice.getId())
                    .status("PAID")
                    .amount(invoice.getAmount())
                    .qrCodeData(qrCodeData)
                    .message("QR code payment processed")
                    .build();
        } else {
            return PaymentResponse.builder()
                    .invoiceId(invoice.getId())
                    .status("UNSUPPORTED_METHOD")
                    .amount(invoice.getAmount())
                    .message("Payment method not supported")
                    .build();
        }
    }

    private String generateQrCodeData(Invoice invoice) {
        // Generate QR code data for payment
        return "QR_CODE_DATA_FOR_INVOICE_" + invoice.getId() + "_AMOUNT_" + invoice.getAmount();
    }
}