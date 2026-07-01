package com.sparta.copa.copaorder.order.client.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 결제 서비스의 카카오 ready 응답(tid + 결제창 리다이렉트 URL).
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class PgReadyView {

  private String tid;
  private String redirectUrl;
}