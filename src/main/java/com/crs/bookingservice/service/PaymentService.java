package com.crs.bookingservice.service;

import com.crs.bookingservice.dto.request.PaymentRequest;
import com.crs.bookingservice.dto.response.PaymentResponse;

public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request);
}