package com.sparta.copa.copaproduct.common.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

// 서비스 경계에서 신뢰를 강제하는 필터.
// - /internal/**: 서비스 토큰(X-Copa-Service) 검증. 없거나 위조면 401. 통과 시 호출자가 넘긴 X-User-*는 신뢰한다.
// - 그 외(게이트웨이 경유): 클라이언트가 붙인 X-User-*는 절대 신뢰하지 않고, 서명된 신원 토큰(X-Copa-Identity)에서만
//   X-User-Id/X-User-Role을 재구성한다. 위조된 신원 토큰이면 401, 없으면 공개 요청으로 통과(컨트롤러가 필요 시 400).
public class InternalAuthFilter extends OncePerRequestFilter {

  public static final String USER_ID_HEADER = "X-User-Id";
  public static final String USER_ROLE_HEADER = "X-User-Role";
  private static final String INTERNAL_PREFIX = "/internal/";

  private final InternalTokenService tokenService;

  public InternalAuthFilter(InternalTokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String path = request.getRequestURI();

    if (path.startsWith(INTERNAL_PREFIX)) {
      Claims claims = tokenService.verifyService(
          request.getHeader(InternalTokenService.SERVICE_HEADER));
      if (claims == null) {
        deny(response);
        return;
      }
      // 호출 서비스가 인증됐으므로, 그가 전달한 X-User-*는 그대로 신뢰한다(내부 신뢰 구역).
      chain.doFilter(request, response);
      return;
    }

    String userId = null;
    String role = null;
    String identity = request.getHeader(InternalTokenService.IDENTITY_HEADER);
    if (identity != null && !identity.isBlank()) {
      Claims claims = tokenService.verifyIdentity(identity);
      if (claims == null) {
        deny(response);
        return;
      }
      userId = claims.getSubject();
      role = claims.get(InternalTokenService.ROLE_CLAIM, String.class);
    }
    // 클라이언트가 직접 붙인 X-User-*는 아래 래퍼가 덮어써(또는 숨겨) 무력화한다.
    chain.doFilter(new IdentityHeaderRequestWrapper(request, userId, role), response);
  }

  private void deny(HttpServletResponse response) throws IOException {
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write("{\"error\":\"Unauthorized\"}");
  }
}