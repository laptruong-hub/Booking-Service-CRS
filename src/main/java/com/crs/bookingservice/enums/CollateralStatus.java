package com.crs.bookingservice.enums;

/**
 * Trạng thái tài sản thế chấp
 */
public enum CollateralStatus {
    HELD, // Đang giữ
    RETURNED, // Đã trả lại
    FORFEITED // Bị tịch thu (do vi phạm hợp đồng)
}
