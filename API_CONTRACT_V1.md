# API Contract v1 - Booking Service (Step 1-9)
**Generated Date:** 2026-03-17  
**Version:** 1.0  
**Status:** Ready for Integration

---

## 1. Staff bàn giao xe (PICKUP)

### Endpoint
```
PATCH /api/v1/bookings/{id}/staff-handover-start
```

**Parameters:**
- `{id}` (Path): bookingId (Long)

### Request

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "rentalUnitId": 101,
  "type": "PICKUP",
  "odoMeter": 12345.6,
  "condition": "Xe bình thường, không móp méo, không vết xước",
  "vehiclePhotos": [
    {
      "corner": "FRONT_LEFT",
      "imageUrl": "https://cdn.example.com/rental-101-fl-pickup.jpg"
    },
    {
      "corner": "FRONT_RIGHT",
      "imageUrl": "https://cdn.example.com/rental-101-fr-pickup.jpg"
    },
    {
      "corner": "REAR_LEFT",
      "imageUrl": "https://cdn.example.com/rental-101-rl-pickup.jpg"
    },
    {
      "corner": "REAR_RIGHT",
      "imageUrl": "https://cdn.example.com/rental-101-rr-pickup.jpg"
    }
  ]
}
```

**Field Validations:**
- `rentalUnitId`: required, positive integer
- `type`: required, fixed value = "PICKUP"
- `odoMeter`: required, positive decimal
- `condition`: optional, string (max 255 chars)
- `vehiclePhotos`: required, array length must be exactly 4
  - `corner`: required, enum = [FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT]
  - `imageUrl`: required, must start with http:// or https://, must not be blank
  - All 4 corners must be present, no duplicates

**Validation Error Examples:**
```json
{
  "success": false,
  "message": "Phải có đủ 4 góc: FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT và không được trùng"
}
```

### Response (Success)

**Status Code:** 200 OK

**Body:**
```json
{
  "success": true,
  "message": "Đã bàn giao xe, chuyến đi bắt đầu.",
  "data": {
    "id": 1,
    "userId": "user-uuid-001",
    "bookingCode": "BK-20260317-001",
    "customerName": "Nguyễn Văn A",
    "customerEmail": "nguyenvan.a@example.com",
    "customerPhone": "0901234567",
    "deliveryMode": "SELF_PICKUP",
    "deliveryAddress": null,
    "deliveryFee": 0,
    "totalAmount": 5000000,
    "depositRequired": 1000000,
    "status": "IN_PROGRESS",
    "createdAt": "2026-03-17T08:00:00",
    "rentalUnits": [
      {
        "id": 101,
        "vehicleId": 555,
        "driverId": null,
        "isWithDriver": false,
        "vehiclePlateNumber": "29A-12345",
        "vehicleBrand": "Toyota",
        "vehicleModel": "Camry",
        "vehicleStatus": "AVAILABLE",
        "driverName": null,
        "driverPhone": null,
        "startTime": "2026-03-17T08:00:00",
        "endTime": "2026-03-20T08:00:00",
        "actualReturnTime": null,
        "unitPrice": 5000000,
        "faultPercent": 0,
        "status": "ACTIVE",
        "inspectionAnalysisId": 9001,
        "inspectionStage": "PICKUP",
        "inspectionStatus": "SUCCESS",
        "inspectionSeverity": "LOW",
        "comparisonSummary": null,
        "inspectionRecommendedFee": 0,
        "needsManualReview": false,
        "newDamageDetected": null
      }
    ]
  }
}
```

**Response Fields Explanation:**
- `rentalUnits[x].inspectionAnalysisId`: ID của bản phân tích AI (null nếu AI chưa chạy hoặc lỗi)
- `rentalUnits[x].inspectionStage`: "PICKUP" | "RETURN"
- `rentalUnits[x].inspectionStatus`: "SUCCESS" | "RATE_LIMIT" | "TIMEOUT" | "RETRYABLE_ERROR" | "FEIGN_xxx" | "UNEXPECTED_ERROR"
- `rentalUnits[x].inspectionSeverity`: "NONE" | "LOW" | "MEDIUM" | "HIGH"
- `rentalUnits[x].comparisonSummary`: null ở PICKUP, có dữ liệu ở RETURN
- `rentalUnits[x].inspectionRecommendedFee`: phí AI gợi ý (0 nếu không phát hiện hư hại)
- `rentalUnits[x].needsManualReview`: true nếu AI confidence thấp, AI lỗi, hoặc định dạng lỗi
- `rentalUnits[x].newDamageDetected`: null ở PICKUP

---

## 2. Staff nhận xe trả (RETURN)

### Endpoint
```
PATCH /api/v1/bookings/{id}/staff-handover-return
```

**Parameters:**
- `{id}` (Path): bookingId (Long)

### Request

**Headers:**
```
Content-Type: application/json
```

**Body:**
```json
{
  "rentalUnitId": 101,
  "type": "RETURN",
  "odoMeter": 12420.8,
  "condition": "Có vết xước cản sau, lốp trước trái không đều",
  "vehiclePhotos": [
    {
      "corner": "FRONT_LEFT",
      "imageUrl": "https://cdn.example.com/rental-101-fl-return.jpg"
    },
    {
      "corner": "FRONT_RIGHT",
      "imageUrl": "https://cdn.example.com/rental-101-fr-return.jpg"
    },
    {
      "corner": "REAR_LEFT",
      "imageUrl": "https://cdn.example.com/rental-101-rl-return.jpg"
    },
    {
      "corner": "REAR_RIGHT",
      "imageUrl": "https://cdn.example.com/rental-101-rr-return.jpg"
    }
  ]
}
```

**Field Validations:** (Giống PICKUP)
- `type`: fixed value = "RETURN"
- `vehiclePhotos`: phải đủ 4 góc, giống PICKUP

### Response (Success)

**Status Code:** 200 OK

**Body:**
```json
{
  "success": true,
  "message": "Đã nhận xe lại, booking hoàn tất.",
  "data": {
    "id": 1,
    "userId": "user-uuid-001",
    "bookingCode": "BK-20260317-001",
    "status": "COMPLETED",
    "rentalUnits": [
      {
        "id": 101,
        "vehicleId": 555,
        "status": "RETURNED",
        "inspectionAnalysisId": 9002,
        "inspectionStage": "RETURN",
        "inspectionStatus": "SUCCESS",
        "inspectionSeverity": "MEDIUM",
        "comparisonSummary": "Phát hiện 1 hư hại mới so với PICKUP: vết xước cản sau 15cm.",
        "inspectionRecommendedFee": 2000000,
        "needsManualReview": false,
        "newDamageDetected": true,
        "actualReturnTime": "2026-03-20T08:30:00"
      }
    ]
  }
}
```

**Response Fields Explanation (RETURN specific):**
- `comparisonSummary`: so sánh ảnh RETURN với baseline PICKUP, liệt kê hư hại mới
- `newDamageDetected`: true = phát hiện hư hại mới, false = không có hư hại mới
- `inspectionRecommendedFee`: phí dựa trên severity + policy nội bộ (có thể khác AI raw)

---

## 3. Lấy chi tiết booking (bao gồm dữ liệu AI)

### Endpoint (Query)
```
GET /api/v1/bookings/{id}
```

**Parameters:**
- `{id}` (Path): bookingId (Long)

### Response

**Status Code:** 200 OK

**Body:** (Tương tự như section 1 hoặc 2, tùy trạng thái)
```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 1,
    "bookingCode": "BK-20260317-001",
    "status": "COMPLETED",
    "rentalUnits": [
      {
        "id": 101,
        "inspectionAnalysisId": 9002,
        "inspectionStage": "RETURN",
        "inspectionStatus": "SUCCESS",
        "inspectionSeverity": "MEDIUM",
        "comparisonSummary": "Phát hiện 1 hư hại mới so với PICKUP",
        "inspectionRecommendedFee": 2000000,
        "needsManualReview": false,
        "newDamageDetected": true
      }
    ]
  }
}
```

---

## 4. Lấy chi tiết booking theo mã

### Endpoint
```
GET /api/v1/bookings/code/{bookingCode}
```

**Parameters:**
- `{bookingCode}` (Path): string (ví dụ: "BK-20260317-001")

### Response

**Status Code:** 200 OK  
(Giống section 3)

---

## AI Inspection Field Reference

### inspectionStatus - Trạng thái xử lý AI

| Status | Ý nghĩa | Hành động FE |
|--------|---------|----------|
| SUCCESS | AI xử lý thành công | Hiển thị kết quả, severity, phí gợi ý |
| RATE_LIMIT | API quota Gemini hết | Hiển thị cảnh báo, đánh dấu cần review |
| TIMEOUT | Timeout khi gọi Gemini | Hiển thị cảnh báo, đánh dấu cần review |
| RETRYABLE_ERROR | Lỗi tạm thời (network) | Hiển thị cảnh báo, FE có thể retry sau |
| FEIGN_xxx | Lỗi HTTP từ Gemini (xxx = status code) | Hiển thị thông báo lỗi, staff review |
| UNEXPECTED_ERROR | Lỗi ngoài dự tính | Hiển thị cảnh báo, log error |

### inspectionSeverity - Mức độ hư hại phát hiện

| Severity | Ý nghĩa | Phí tương đương |
|----------|---------|-----------------|
| NONE | Không phát hiện hư hại | 0 VND |
| LOW | Hư hại nhẹ (trầy xước, dent nhỏ) | 500,000 - 1,000,000 VND |
| MEDIUM | Hư hại trung bình | 2,000,000 VND |
| HIGH | Hư hại nặng (cửa méo, kính vỡ) | 5,000,000+ VND |

---

## Lưu ý quan trọng

### 1. Naming Convention
- Tên field trả về hiện tại: `inspectionRecommendedFee`, `inspectionAnalysisId`, `inspectionStatus`, v.v.
- FE cần map theo tên chính xác này trong response.

### 2. AI gọi async
- AI chạy **ngay sau** khi tạo biên bản PICKUP/RETURN.
- Flow giao/nhận xe **không chờ** kết quả AI.
- Nếu AI lỗi, biên bản vẫn được tạo, chỉ mark `needsManualReview = true`.

### 3. Baseline so sánh
- PICKUP: lưu baseline, `comparisonSummary = null`
- RETURN: so sánh với baseline PICKUP, `comparisonSummary` có dữ liệu

### 4. newDamageDetected
- **PICKUP:** null (không có so sánh)
- **RETURN:** true = phát hiện hư hại mới, false = không có hư hại mới

### 5. Phí gợi ý
- `inspectionRecommendedFee` = max(policy_fee, ai_suggested_fee)
- **Chưa phải** phí chốt cuối cùng.
- Staff phải duyệt lại trước khi hoàn tất booking.

---

## Test Cases FE cần chuẩn bị

1. **Đủ 4 ảnh, giá trị valid**: Success 200, dữ liệu AI xuất hiện
2. **Thiếu ảnh (<4)**: Validation error 400
3. **Sai góc ảnh**: Validation error 400
4. **URL ảnh không valid**: Validation error 400
5. **AI timeout**: Success 200, `inspectionStatus = "TIMEOUT"`, `needsManualReview = true`
6. **AI format lỗi**: Success 200, `inspectionStatus = "SUCCESS"`, fallback summary
7. **Thiếu baseline (RETURN không tìm PICKUP)**: Success 200, `comparisonSummary` ghi rõ thiếu baseline
8. **Confidence thấp**: Success 200, `needsManualReview = true`

---

## Ví dụ Full Flow

### Bước 1: Staff giao xe (PICKUP)
```
PATCH /api/v1/bookings/1/staff-handover-start
Request: {rentalUnitId: 101, type: "PICKUP", odoMeter: 12000, vehiclePhotos: [...]}
Response: inspectionAnalysisId = 9001, inspectionSeverity = "LOW", inspectionStatus = "SUCCESS"
```

### Bước 2: Khách mở xe, lái trong 3 ngày
(Không có API call)

### Bước 3: Staff nhận xe (RETURN)
```
PATCH /api/v1/bookings/1/staff-handover-return
Request: {rentalUnitId: 101, type: "RETURN", odoMeter: 12100, vehiclePhotos: [...]}
Response: 
  - inspectionAnalysisId = 9002
  - comparisonSummary = "Phát hiện 1 hư hại mới: cán trái có vết xước"
  - newDamageDetected = true
  - inspectionRecommendedFee = 1500000
  - needsManualReview = false
```

### Bước 4: FE hiển thị
- Hiển thị hư hại mới từ `comparisonSummary`
- Hiển thị phí gợi ý từ `inspectionRecommendedFee`
- Staff có thể chỉnh sửa phí trước hoàn tất

---

**Version History:**
- v1.0 (2026-03-17): Initial API Contract for Steps 1-9
