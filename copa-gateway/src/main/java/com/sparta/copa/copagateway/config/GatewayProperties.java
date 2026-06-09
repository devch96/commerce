package com.sparta.copa.copagateway.config;

import java.net.URI;
import java.util.List;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

  private final URI authServiceUri;
  private final List<String> whitelist;

  public GatewayProperties(URI authServiceUri, List<String> whitelist) {
    this.authServiceUri = authServiceUri;
    this.whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
  }
}