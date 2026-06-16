package com.sparta.copa.copaorder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class CopaOrderApplication {

  public static void main(String[] args) {
    SpringApplication.run(CopaOrderApplication.class, args);
  }

}
