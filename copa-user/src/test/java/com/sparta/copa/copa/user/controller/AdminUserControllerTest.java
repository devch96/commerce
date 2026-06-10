package com.sparta.copa.copa.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copa.user.domain.UserRole;
import com.sparta.copa.copa.user.dto.request.ChangeRoleRequest;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserService userService;

  @Test
  @DisplayName("PATCH /users/{id}/role - ADMIN이 회원을 SELLER로 승격한다")
  void changeRoleAsAdmin() throws Exception {
    ChangeRoleRequest request = ChangeRoleRequest.builder().role(UserRole.SELLER).build();
    given(userService.changeRole(eq(1L), eq(UserRole.SELLER))).willReturn(
        UserResponse.builder().id(1L).email("u@copa.com").name("코파").role(UserRole.SELLER).build());

    mockMvc.perform(patch("/users/1/role")
            .header("X-User-Role", "ADMIN")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.role").value("SELLER"));
  }

  @Test
  @DisplayName("PATCH /users/{id}/role - ADMIN이 아니면 403을 반환하고 변경하지 않는다")
  void changeRoleForbiddenForNonAdmin() throws Exception {
    ChangeRoleRequest request = ChangeRoleRequest.builder().role(UserRole.SELLER).build();

    mockMvc.perform(patch("/users/1/role")
            .header("X-User-Role", "USER")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());

    verify(userService, never()).changeRole(any(), any());
  }
}
