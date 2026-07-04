package com.sparta.copa.copacoupon.fcfs.dto;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 선착순 오픈 요청. quantity를 생략하면 서버 기본값(임시 1000장)을 시드한다.
 */
@Getter
@NoArgsConstructor
public class FcfsOpenRequest {

  @Positive
  private Integer quantity;
}
