package com.sparta.copa.copapayment.payment.domain;

// 지원 PG. 결제·취소 시 게이트웨이 라우팅 키로 쓴다.
public enum PgProvider {
  TOSS,
  KAKAO
}