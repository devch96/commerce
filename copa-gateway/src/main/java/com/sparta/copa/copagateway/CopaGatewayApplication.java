package com.sparta.copa.copagateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CopaGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(CopaGatewayApplication.class, args);
  }

}
