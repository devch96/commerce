package com.sparta.copa.copa.user.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sparta.copa.copa.user.domain.UserRole;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
class UserControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @Test
  @DisplayName("GET /users/me - X-User-Id 헤더로 내 정보를 조회한다")
  void me() throws Exception {
    given(userService.getProfile(1L)).willReturn(
        UserResponse.builder().id(1L).email("user@copa.com").name("코파").role(UserRole.USER).build());

    mockMvc.perform(get("/users/me").header("X-User-Id", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(1))
        .andExpect(jsonPath("$.data.email").value("user@copa.com"));
  }

  @Test
  @DisplayName("GET /users/me - X-User-Id 헤더가 없으면 400을 반환한다")
  void meMissingHeader() throws Exception {
    mockMvc.perform(get("/users/me"))
        .andExpect(status().isBadRequest());
  }
}
