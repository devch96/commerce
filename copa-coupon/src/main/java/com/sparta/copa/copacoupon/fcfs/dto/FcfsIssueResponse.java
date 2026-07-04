package com.sparta.copa.copacoupon.fcfs.dto;

import lombok.Getter;

/**
 * 선착순 발급 접수 응답. DB 반영은 Kafka로 비동기 처리되므로 즉시 UserCoupon 행을 돌려주지 않고,
 * "발급이 확정(선착순 통과)되어 접수됐다"는 사실과 남은 재고만 알린다. 실제 보유 쿠폰은 /coupons/me에서 확인한다.
 */
@Getter
public class FcfsIssueResponse {

  // 선착순 통과 후 비동기 DB 반영 대기 상태.
  private static final String STATUS_ACCEPTED = "ACCEPTED";

  private final Long couponId;
  private final Long userId;
  private final String status;
  private final long remainingStock;

  private FcfsIssueResponse(Long couponId, Long userId, String status, long remainingStock) {
    this.couponId = couponId;
    this.userId = userId;
    this.status = status;
    this.remainingStock = remainingStock;
  }

  public static FcfsIssueResponse accepted(Long couponId, Long userId, long remainingStock) {
    return new FcfsIssueResponse(couponId, userId, STATUS_ACCEPTED, remainingStock);
  }
}
