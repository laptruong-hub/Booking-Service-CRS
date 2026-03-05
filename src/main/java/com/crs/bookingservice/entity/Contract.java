package com.crs.bookingservice.entity;

import com.crs.bookingservice.enums.ContractStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * CONTRACT — Hợp đồng thuê xe cho một booking (RentalGroup)
 */
@Entity
@Table(name = "contract", indexes = {
        @Index(name = "idx_contract_rental_group", columnList = "rental_group_id"),
        @Index(name = "idx_contract_number", columnList = "contract_number")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_group_id", nullable = false, unique = true)
    RentalGroup rentalGroup;

    @Column(name = "contract_number", nullable = false, unique = true, length = 50)
    String contractNumber;

    /**
     * URL file PDF hợp đồng (lưu trên S3/MinIO/...)
     */
    @Column(name = "pdf_url", length = 500)
    String pdfUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ContractStatus status = ContractStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
}
