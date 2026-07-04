package com.sparta.copa.copacoupon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * 선착순 발급 Lua 스크립트를 RedisScript 빈으로 로드한다.
 * EVAL 대신 SCRIPT LOAD 캐시(EVALSHA)를 쓰도록 스크립트 본문을 클래스패스에서 한 번만 읽는다.
 */
@Configuration
public class RedisScriptConfig {

  @Bean
  public RedisScript<Long> couponIssueScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/coupon_issue.lua")));
    script.setResultType(Long.class);
    return script;
  }
}
