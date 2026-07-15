package com.sparta.copa.copaticket.ticket.repository;

import com.sparta.copa.copaticket.ticket.domain.Ticket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

  // 컨슈머 멱등 INSERT의 선존재 검사(정상 중복 no-op). 드문 동시 경합은 (event_id,user_id) 유니크가 흡수.
  boolean existsByEvent_IdAndUserId(Long eventId, Long userId);

  Optional<Ticket> findByTicketNo(String ticketNo);

  List<Ticket> findByUserIdOrderByIdDesc(Long userId);
}