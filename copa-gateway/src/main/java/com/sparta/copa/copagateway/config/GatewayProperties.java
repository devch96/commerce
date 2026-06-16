package com.sparta.copa.copagateway.config;

import java.net.URI;
import java.util.List;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

  private final URI authServiceUri;
  private final URI productServiceUri;
  private final URI orderServiceUri;
  private final URI paymentServiceUri;
  private final List<String> whitelist;

  public GatewayProperties(URI authServiceUri, URI productServiceUri, URI orderServiceUri,
      URI paymentServiceUri, List<String> whitelist) {
    this.authServiceUri = authServiceUri;
    this.productServiceUri = productServiceUri;
    this.orderServiceUri = orderServiceUri;
    this.paymentServiceUri = paymentServiceUri;
    this.whitelist = whitelist == null ? List.of() : List.copyOf(whitelist);
  }
}