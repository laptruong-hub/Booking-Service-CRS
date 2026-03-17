package com.crs.bookingservice.config;

import com.crs.bookingservice.entity.PaymentMethod;
import com.crs.bookingservice.enums.PaymentMethodType;
import com.crs.bookingservice.repository.PaymentMethodRepository;
import com.crs.bookingservice.entity.SurchargePolicy;
import com.crs.bookingservice.repository.SurchargePolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

/**
 * DataInitializer — khởi tạo dữ liệu mẫu ban đầu.
 * Chỉ chạy khi bảng trống (idempotent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class DataInitializer implements CommandLineRunner {

    private final PaymentMethodRepository paymentMethodRepository;
    private final SurchargePolicyRepository surchargePolicyRepository;
    @Override
    @Transactional
    public void run(String... args) {
        if (paymentMethodRepository.count() == 0) {
            log.info("Khởi tạo dữ liệu mẫu cho Booking Service...");

            List<PaymentMethod> methods = List.of(
                    PaymentMethod.builder()
                            .methodType(PaymentMethodType.CASH)
                            .displayName("Tiền mặt")
                            .isActive(true)
                            .build(),
                    PaymentMethod.builder()
                            .methodType(PaymentMethodType.BANK_TRANSFER)
                            .displayName("Chuyển khoản ngân hàng")
                            .isActive(true)
                            .build(),
                    PaymentMethod.builder()
                            .methodType(PaymentMethodType.CREDIT_CARD)
                            .displayName("Thẻ tín dụng")
                            .isActive(true)
                            .build(),
                    PaymentMethod.builder()
                            .methodType(PaymentMethodType.E_WALLET)
                            .displayName("Ví điện tử (Momo/ZaloPay)")
                            .isActive(true)
                            .build());

            paymentMethodRepository.saveAll(methods);
            log.info("Đã khởi tạo {} phương thức thanh toán.", methods.size());
        }
    }

    /**
     * Seed 4 global damage policies (rental_unit = NULL).
     *
     * Mapping từ AI severity → policy code → fee mặc định:
     *   HIGH    → DAMAGE_HIGH    → 5,000,000 VND
     *   MEDIUM  → DAMAGE_MEDIUM  → 2,000,000 VND
     *   LOW     → DAMAGE_LOW     →   500,000 VND
     *   NONE    → DAMAGE_GENERAL →         0 VND (không sinh fee vì <= 0)
     *
     * Staff có thể điều chỉnh feeAmount thủ công trong DB cho từng trường hợp.
     * Per-unit policy (rental_unit_id NOT NULL) sẽ được ưu tiên hơn global.
     */
    private void seedGlobalDamagePolicies() {
        boolean alreadySeeded = surchargePolicyRepository
                .findFirstByRentalUnitIsNullAndCodeIgnoreCaseOrderByIdAsc("DAMAGE_HIGH")
                .isPresent();
        if (alreadySeeded) {
            return;
        }

        log.info("Khởi tạo global damage surcharge policies...");
        List<SurchargePolicy> policies = List.of(
                SurchargePolicy.builder()
                        .rentalUnit(null)
                        .code("DAMAGE_HIGH")
                        .feeAmount(new BigDecimal("5000000"))
                        .build(),
                SurchargePolicy.builder()
                        .rentalUnit(null)
                        .code("DAMAGE_MEDIUM")
                        .feeAmount(new BigDecimal("2000000"))
                        .build(),
                SurchargePolicy.builder()
                        .rentalUnit(null)
                        .code("DAMAGE_LOW")
                        .feeAmount(new BigDecimal("500000"))
                        .build(),
                SurchargePolicy.builder()
                        .rentalUnit(null)
                        .code("DAMAGE_GENERAL")
                        .feeAmount(BigDecimal.ZERO)
                        .build()
        );
        surchargePolicyRepository.saveAll(policies);
        log.info("Đã khởi tạo {} global damage policies.", policies.size());
    }
}
