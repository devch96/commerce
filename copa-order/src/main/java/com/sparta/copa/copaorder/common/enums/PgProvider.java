package com.sparta.copa.copaorder.common.enums;

// 결제에 사용할 PG. 주문 생성(카카오 ready)·확정(승인) 시 어느 결제 경로를 탈지 결정.
public enum PgProvider {
  TOSS,
  KAKAO
}