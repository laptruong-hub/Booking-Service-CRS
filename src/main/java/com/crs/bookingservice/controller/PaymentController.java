package com.crs.bookingservice.controller;

import com.crs.bookingservice.dto.request.PaymentRequest;
import com.crs.bookingservice.dto.response.ApiResponse;
import com.crs.bookingservice.dto.response.PaymentResponse;
import com.crs.bookingservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(@Valid @RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(ApiResponse.<PaymentResponse>builder()
                .success(true)
                .data(response)
                .build());
    }
}