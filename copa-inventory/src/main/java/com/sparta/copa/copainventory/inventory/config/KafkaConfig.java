package com.sparta.copa.copainventory.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

/**
 * Kafka 리스너 설정. `product-events` 단일 토픽에 여러 eventType이 흐를 수 있으므로,
 * 재고가 관심 있는 타입만 ProductEventRecordFilter(RecordFilterStrategy)로 통과시킨다.
 */
@Configuration
public class KafkaConfig {

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> productEventListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setRecordFilterStrategy(new ProductEventRecordFilter());
    // 폐기한 레코드의 오프셋도 커밋해 같은 메시지를 반복 폴링하지 않게 한다.
    factory.setAckDiscarded(true);
    return factory;
  }
}
