package com.sparta.copa.copaproduct.outbox.repository;

import com.sparta.copa.copaproduct.outbox.domain.OutboxEvent;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

  // 미발행 이벤트를 생성 순서(id 오름차순)로 배치 조회한다.
  @Query("select e from OutboxEvent e where e.publishedAt is null order by e.id asc")
  List<OutboxEvent> findUnpublished(Pageable pageable);
}