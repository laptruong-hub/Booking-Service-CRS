package com.crs.bookingservice.enums;

/**
 * Trạng thái của hợp đồng thuê xe
 */
public enum ContractStatus {
    DRAFT, // Bản nháp, chưa ký
    SIGNED, // Đã ký
    EXPIRED, // Đã hết hạn
    TERMINATED // Đã chấm dứt sớm
}
