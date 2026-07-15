package com.sparta.copa.copaticket.ticket.support;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 외부 노출용 예매 번호 생성기. 주문 서비스의 orderNo 규약을 따른다:
 * TKT-yyyyMMdd-XXXXXX (혼동 문자 0/O·1/I를 뺀 30자 알파벳, 일 단위 7억+ 조합).
 * 충돌은 DB 유니크가 최종 방어선이며 확률상 재시도 코드는 두지 않는다(17주차 5일차 결정과 동일).
 */
@Component
public class TicketNoGenerator {

  private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
  private static final int RANDOM_LENGTH = 6;
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

  private final SecureRandom random = new SecureRandom();

  public String generate() {
    StringBuilder sb = new StringBuilder("TKT-").append(LocalDate.now().format(DATE_FORMAT))
        .append('-');
    for (int i = 0; i < RANDOM_LENGTH; i++) {
      sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    }
    return sb.toString();
  }
}