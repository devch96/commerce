package com.sparta.copa.copainventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 예약 TTL 만료 자동 해제 스케줄러를 위한 스케줄링 활성화.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
