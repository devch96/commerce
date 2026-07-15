package com.sparta.copa.copaticket.event.repository;

import com.sparta.copa.copaticket.common.enums.EventStatus;
import com.sparta.copa.copaticket.event.domain.TicketEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketEventRepository extends JpaRepository<TicketEvent, Long> {

  // 입장 스케줄러가 OPEN 이벤트의 대기열만 처리한다.
  List<TicketEvent> findByStatus(EventStatus status);
}