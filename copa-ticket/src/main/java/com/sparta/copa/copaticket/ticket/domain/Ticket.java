package com.sparta.copa.copaticket.ticket.domain;

import com.sparta.copa.copaticket.common.enums.TicketStatus;
import com.sparta.copa.copaticket.common.exception.BusinessException;
import com.sparta.copa.copaticket.common.exception.ErrorCode;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
 * 발권된 예매 인스턴스. (event_id, user_id) 유니크로 1인 1매를 보장한다(Redis 선착순의 최종 방어선).
 * 외부 식별자는 ticketNo(주문 orderNo와 동일 규약) — 순차 PK 열거를 막는다.
 */
@Entity
@Getter
@Table(name = "tickets")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Ticket {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 외부 노출용 예매 번호(TKT-yyyyMMdd-XXXXXX). 발행 시점에 생성해 이벤트에 실어 컨슈머 멱등 INSERT에 쓴다.
  @Column(name = "ticket_no", nullable = false, length = 30, unique = true)
  private String ticketNo;

  // Ticket -> TicketEvent 단방향 ManyToOne. FK(event_id)는 자식 테이블이 소유한다.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false)
  private TicketEvent event;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TicketStatus status;

  @Version
  private Long version;

  @CreatedDate
  @Column(name = "issued_at", updatable = false)
  private LocalDateTime issuedAt;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Builder
  private Ticket(String ticketNo, TicketEvent event, Long userId) {
    this.ticketNo = ticketNo;
    this.event = event;
    this.userId = userId;
    this.status = TicketStatus.ISSUED;
  }

  public static Ticket issue(String ticketNo, TicketEvent event, Long userId) {
    return Ticket.builder().ticketNo(ticketNo).event(event).userId(userId).build();
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId != null && this.userId.equals(userId);
  }

  // 사용자 취소(ISSUED→CANCELLED). 이미 취소된 건은 상태 충돌로 거절해 좌석 복원이 중복 실행되지 않게 한다.
  public void cancel(LocalDateTime now) {
    if (status != TicketStatus.ISSUED) {
      throw new BusinessException(ErrorCode.TICKET_NOT_CANCELABLE);
    }
    this.status = TicketStatus.CANCELLED;
    this.cancelledAt = now;
  }
}