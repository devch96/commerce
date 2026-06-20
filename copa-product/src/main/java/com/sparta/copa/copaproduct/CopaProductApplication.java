package com.sparta.copa.copaproduct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: outbox 릴레이가 미발행 이벤트를 주기적으로 폴링해 Kafka로 발행한다.
@EnableScheduling
@SpringBootApplication
public class CopaProductApplication {

  public static void main(String[] args) {
    SpringApplication.run(CopaProductApplication.class, args);
  }

}
