package com.sparta.copa.copaticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// 대기열 입장 스케줄러(QueueAdmissionScheduler)를 위해 스케줄링을 활성화한다.
@EnableScheduling
@SpringBootApplication
public class CopaTicketApplication {

  public static void main(String[] args) {
    SpringApplication.run(CopaTicketApplication.class, args);
  }

}