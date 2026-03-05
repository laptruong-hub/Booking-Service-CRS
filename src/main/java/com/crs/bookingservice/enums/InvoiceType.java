package com.crs.bookingservice.enums;

/**
 * Loại hoá đơn
 */
public enum InvoiceType {
    DEPOSIT, // Hoá đơn đặt cọc
    RENTAL, // Hoá đơn thuê xe chính
    INCURRED, // Hoá đơn phát sinh
    REFUND // Hoá đơn hoàn tiền
}
