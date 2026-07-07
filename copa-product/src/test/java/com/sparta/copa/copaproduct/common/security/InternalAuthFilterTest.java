package com.sparta.copa.copaproduct.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

// 내부 인증 필터의 핵심 보안 속성을 검증한다: 위조 헤더 차단, 서명 신원 재구성, /internal 서비스 토큰 강제.
class InternalAuthFilterTest {

  private static final String SECRET =
      "copa-internal-unit-test-secret-key-must-be-at-least-256-bits-long";

  private SecretKey key;
  private InternalAuthFilter filter;

  @BeforeEach
  void setUp() {
    this.key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    this.filter = new InternalAuthFilter(new InternalTokenService(SECRET));
  }

  private String signed(String type, String subject, String role, String svc) {
    Instant now = Instant.now();
    var builder = Jwts.builder()
        .claim("typ", type)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(60)))
        .signWith(key);
    if (subject != null) {
      builder.subject(subject);
    }
    if (role != null) {
      builder.claim("role", role);
    }
    if (svc != null) {
      builder.claim("svc", svc);
    }
    return builder.compact();
  }

  @Test
  @DisplayName("유효한 신원 토큰이면 X-User-Id/X-User-Role을 재구성해 컨트롤러로 전달한다")
  void validIdentityReconstructsUserHeaders() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products");
    request.addHeader("X-Copa-Identity", signed("identity", "42", "ADMIN", null));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("42");
    assertThat(forwarded.getHeader("X-User-Role")).isEqualTo("ADMIN");
  }

  @Test
  @DisplayName("신원 토큰 없이 클라이언트가 직접 붙인 X-User-Role은 제거된다(스푸핑 방지)")
  void forgedUserHeadersAreStrippedWithoutIdentity() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products");
    request.addHeader("X-User-Id", "999");
    request.addHeader("X-User-Role", "ADMIN");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.getHeader("X-User-Id")).isNull();
    assertThat(forwarded.getHeader("X-User-Role")).isNull();
  }

  @Test
  @DisplayName("위조된 신원 토큰은 401을 반환하고 체인을 호출하지 않는다")
  void forgedIdentityTokenIsRejected() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products");
    request.addHeader("X-Copa-Identity", "tampered.identity.token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  @DisplayName("/internal 경로는 서비스 토큰이 없으면 401(게이트웨이 우회 직접 호출 차단)")
  void internalPathWithoutServiceTokenIsRejected() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/internal/products/1/option-price");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  @DisplayName("/internal 경로는 유효한 서비스 토큰이면 통과하고 호출자가 넘긴 X-User-Id를 신뢰한다")
  void internalPathWithServiceTokenPassesThrough() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/internal/products/1/option-price");
    request.addHeader("X-Copa-Service", signed("service", null, null, "copa-order"));
    request.addHeader("X-User-Id", "7");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    HttpServletRequest forwarded = (HttpServletRequest) chain.getRequest();
    assertThat(forwarded).isNotNull();
    assertThat(forwarded.getHeader("X-User-Id")).isEqualTo("7");
  }

  @Test
  @DisplayName("신원 토큰의 typ가 service면 신원으로 인정하지 않는다(토큰 오용 차단)")
  void serviceTokenIsNotAcceptedAsIdentity() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/products");
    request.addHeader("X-Copa-Identity", signed("service", "42", "ADMIN", "copa-order"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }
}
