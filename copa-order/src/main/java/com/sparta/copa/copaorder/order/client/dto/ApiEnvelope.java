package com.sparta.copa.copaorder.order.client.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 다른 서비스의 공통 응답 봉투({success, data, error}) 역직렬화용. 필드 바인딩(세터 미사용).
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ApiEnvelope<T> {

  private boolean success;
  private T data;
  private String error;
}
