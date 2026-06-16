package com.sparta.copa.copapayment.common.enums;

// 결제 상태. REQUESTED(요청 기록) → APPROVED(PG 승인) / FAILED(거절·오류) → CANCELLED(취소·환불 보상).
public enum PaymentStatus {
  REQUESTED,
  APPROVED,
  FAILED,
  CANCELLED
}
