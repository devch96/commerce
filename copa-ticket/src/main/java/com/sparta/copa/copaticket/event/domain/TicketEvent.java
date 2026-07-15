package com.sparta.copa.copaticket.event.domain;

import com.sparta.copa.copaticket.common.enums.EventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 선착순 예매 이벤트(공연/행사). 좌석 수량의 실시간 권위는 Redis(ticket:{id}:stock)이고,
 * 이 엔티티는 정의(총 좌석·가격)와 상태 전이(SCHEDULED→OPEN→CLOSED)를 관리한다.
 */
@Entity
@Getter
@Table(name = "ticket_events")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TicketEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, length = 100)
  private String venue;

  // 좌석 단가. 결제 연동(Phase 2)의 금액 스냅샷 근거.
  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal price;

  @Column(name = "total_seats", nullable = false)
  private int totalSeats;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventStatus status;

  // 예매 오픈 예정 시각(정보성). 실제 오픈은 관리자 open API가 Redis 시드와 함께 수행한다.
  @Column(name = "open_at")
  private LocalDateTime openAt;

  @Version
  private Long version;

  @CreatedDate
  @Column(name = "created_at", updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private TicketEvent(String name, String venue, BigDecimal price, int totalSeats,
      LocalDateTime openAt) {
    this.name = name;
    this.venue = venue;
    this.price = price;
    this.totalSeats = totalSeats;
    this.status = EventStatus.SCHEDULED;
    this.openAt = openAt;
  }

  public static TicketEvent create(String name, String venue, BigDecimal price, int totalSeats,
      LocalDateTime openAt) {
    return TicketEvent.builder()
        .name(name).venue(venue).price(price).totalSeats(totalSeats).openAt(openAt).build();
  }

  public boolean isOpen() {
    return status == EventStatus.OPEN;
  }

  // 오픈 전이. 재오픈(CLOSED→OPEN)도 허용한다(좌석 시드는 발급분을 제외하고 다시 계산).
  public void open() {
    this.status = EventStatus.OPEN;
  }

  public void close() {
    this.status = EventStatus.CLOSED;
  }
}