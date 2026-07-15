package com.sparta.copa.copaticket.event.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

@Getter
public class EventCreateRequest {

  @NotBlank(message = "이벤트 이름은 필수입니다.")
  private final String name;

  @NotBlank(message = "장소는 필수입니다.")
  private final String venue;

  @NotNull(message = "가격은 필수입니다.")
  @DecimalMin(value = "0", message = "가격은 0 이상이어야 합니다.")
  private final BigDecimal price;

  @Min(value = 1, message = "총 좌석 수는 1 이상이어야 합니다.")
  private final int totalSeats;

  // 오픈 예정 시각(선택, 정보성).
  private final LocalDateTime openAt;

  public EventCreateRequest(String name, String venue, BigDecimal price, int totalSeats,
      LocalDateTime openAt) {
    this.name = name;
    this.venue = venue;
    this.price = price;
    this.totalSeats = totalSeats;
    this.openAt = openAt;
  }
}