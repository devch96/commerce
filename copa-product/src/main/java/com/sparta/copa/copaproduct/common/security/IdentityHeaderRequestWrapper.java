package com.sparta.copa.copaproduct.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// X-User-Id/X-User-Role 헤더를 검증된 신원 토큰 값으로 강제한다.
// 클라이언트가 직접 붙인 원시 값은 노출하지 않으며(숨김), 신원이 없으면 해당 헤더도 없는 것으로 취급한다.
public class IdentityHeaderRequestWrapper extends HttpServletRequestWrapper {

  private final String userId;
  private final String role;

  public IdentityHeaderRequestWrapper(HttpServletRequest request, String userId, String role) {
    super(request);
    this.userId = userId;
    this.role = role;
  }

  @Override
  public String getHeader(String name) {
    if (InternalAuthFilter.USER_ID_HEADER.equalsIgnoreCase(name)) {
      return userId;
    }
    if (InternalAuthFilter.USER_ROLE_HEADER.equalsIgnoreCase(name)) {
      return role;
    }
    return super.getHeader(name);
  }

  @Override
  public Enumeration<String> getHeaders(String name) {
    if (InternalAuthFilter.USER_ID_HEADER.equalsIgnoreCase(name)) {
      return single(userId);
    }
    if (InternalAuthFilter.USER_ROLE_HEADER.equalsIgnoreCase(name)) {
      return single(role);
    }
    return super.getHeaders(name);
  }

  @Override
  public Enumeration<String> getHeaderNames() {
    Set<String> names = new LinkedHashSet<>();
    Enumeration<String> original = super.getHeaderNames();
    while (original.hasMoreElements()) {
      String name = original.nextElement();
      if (!InternalAuthFilter.USER_ID_HEADER.equalsIgnoreCase(name)
          && !InternalAuthFilter.USER_ROLE_HEADER.equalsIgnoreCase(name)) {
        names.add(name);
      }
    }
    if (userId != null) {
      names.add(InternalAuthFilter.USER_ID_HEADER);
    }
    if (role != null) {
      names.add(InternalAuthFilter.USER_ROLE_HEADER);
    }
    return Collections.enumeration(names);
  }

  private Enumeration<String> single(String value) {
    return value == null ? Collections.emptyEnumeration()
        : Collections.enumeration(List.of(value));
  }
}