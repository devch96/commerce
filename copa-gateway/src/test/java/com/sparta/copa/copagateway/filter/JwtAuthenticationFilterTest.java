package com.sparta.copa.copagateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.copa.copagateway.config.GatewayProperties;
import com.sparta.copa.copagateway.config.JwtProperties;
import com.sparta.copa.copagateway.jwt.InternalTokenIssuer;
import com.sparta.copa.copagateway.jwt.JwtProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtAuthenticationFilterTest {

  private static final String SECRET =
      "copa-gateway-unit-test-secret-key-must-be-at-least-256-bits-long";
  private static final String INTERNAL_SECRET =
      "copa-internal-unit-test-secret-key-must-be-at-least-256-bits-long";
  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String IDENTITY_HEADER = "X-Copa-Identity";

  private SecretKey key;
  private SecretKey internalKey;
  private JwtAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    this.key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    this.internalKey = Keys.hmacShaKeyFor(INTERNAL_SECRET.getBytes(StandardCharsets.UTF_8));
    JwtProvider jwtProvider = new JwtProvider(new JwtProperties(SECRET));
    InternalTokenIssuer internalTokenIssuer = new InternalTokenIssuer(INTERNAL_SECRET);
    GatewayProperties gatewayProperties = new GatewayProperties(
        URI.create("http://localhost:8081"),
        URI.create("http://localhost:8082"),
        URI.create("http://localhost:8085"),
        URI.create("http://localhost:8084"),
        URI.create("http://localhost:8086"),
        List.of("/auth/login", "/auth/signup", "/auth/reissue", "GET /products", "GET /products/**"));
    this.filter = new JwtAuthenticationFilter(jwtProvider, internalTokenIssuer, gatewayProperties);
  }

  // 게이트웨이가 발급한 신원 토큰의 클레임(uid/role)을 검증한다.
  private Claims parseIdentity(HttpHeaders forwarded) {
    String identity = forwarded.getFirst(IDENTITY_HEADER);
    assertThat(identity).isNotNull();
    return Jwts.parser().verifyWith(internalKey).build()
        .parseSignedClaims(identity).getPayload();
  }

  private String token(String userId, String role) {
    return Jwts.builder()
        .subject(userId)
        .claim("role", role)
        .signWith(key)
        .compact();
  }

  private CapturingChain capturingChain() {
    return new CapturingChain();
  }

  @Test
  @DisplayName("화이트리스트 경로는 토큰 없이도 하위 서비스로 통과시킨다")
  void whitelistedPathPassesThroughWithoutToken() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/auth/login"));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chain.wasCalled()).isTrue();
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  @DisplayName("유효한 토큰이면 서명된 신원 토큰(X-Copa-Identity)을 실어 하위 서비스로 전달한다")
  void validTokenForwardsSignedIdentity() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("42", "USER")));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    HttpHeaders forwarded = chain.capturedRequestHeaders();
    // 원시 X-User-* 헤더는 전달하지 않고 서명 토큰만 전파한다.
    assertThat(forwarded.getFirst(USER_ID_HEADER)).isNull();
    assertThat(forwarded.getFirst(USER_ROLE_HEADER)).isNull();
    Claims claims = parseIdentity(forwarded);
    assertThat(claims.getSubject()).isEqualTo("42");
    assertThat(claims.get("role", String.class)).isEqualTo("USER");
  }

  @Test
  @DisplayName("토큰이 없는 보호 경로는 401을 반환하고 체인을 호출하지 않는다")
  void missingTokenReturnsUnauthorized() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/auth/me"));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chain.wasCalled()).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("위조된 토큰은 401을 반환한다")
  void invalidTokenReturnsUnauthorized() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer tampered.token.value"));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chain.wasCalled()).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("클라이언트가 주입한 신뢰 헤더는 제거되고, 신원은 서명 토큰에서만 나온다(스푸핑 방지)")
  void clientInjectedTrustedHeadersAreStrippedAndIdentityIsSigned() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/auth/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("42", "USER"))
            .header(USER_ID_HEADER, "999")
            .header(USER_ROLE_HEADER, "ADMIN")
            .header(IDENTITY_HEADER, "forged.identity.token"));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    HttpHeaders forwarded = chain.capturedRequestHeaders();
    assertThat(forwarded.getFirst(USER_ID_HEADER)).isNull();
    assertThat(forwarded.getFirst(USER_ROLE_HEADER)).isNull();
    Claims claims = parseIdentity(forwarded);
    assertThat(claims.getSubject()).isEqualTo("42");
    assertThat(claims.get("role", String.class)).isEqualTo("USER");
  }

  @Test
  @DisplayName("상품 조회(GET /products)는 토큰 없이도 통과시킨다")
  void productReadIsPublic() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.get("/products/123"));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chain.wasCalled()).isTrue();
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  @DisplayName("상품 등록(POST /products)은 메서드가 달라 화이트리스트에 걸리지 않고, 토큰 없으면 401")
  void productWriteRequiresToken() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/products"));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chain.wasCalled()).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("화이트리스트 경로라도 클라이언트가 주입한 신뢰 헤더는 제거된다")
  void clientInjectedTrustedHeadersStrippedOnWhitelistedPath() {
    MockServerWebExchange exchange = MockServerWebExchange.from(
        MockServerHttpRequest.post("/auth/login")
            .header(USER_ID_HEADER, "999"));
    CapturingChain chain = capturingChain();

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chain.capturedRequestHeaders().getFirst(USER_ID_HEADER)).isNull();
  }

  private static final class CapturingChain implements GatewayFilterChain {

    private final AtomicReference<ServerWebExchange> captured = new AtomicReference<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange) {
      captured.set(exchange);
      return Mono.empty();
    }

    boolean wasCalled() {
      return captured.get() != null;
    }

    HttpHeaders capturedRequestHeaders() {
      ServerWebExchange exchange = captured.get();
      if (exchange == null) {
        throw new IllegalStateException("필터 체인이 호출되지 않았습니다");
      }
      return exchange.getRequest().getHeaders();
    }
  }
}