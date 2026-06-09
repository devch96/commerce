package com.sparta.copa.copa.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.user.domain.User;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock
  private UserRepository userRepository;

  @InjectMocks
  private UserService userService;

  private User userWithId(Long id) {
    User user = User.create("user@copa.com", "encoded-password", "코파");
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }

  @Test
  @DisplayName("신규 이메일이면 사용자를 저장한다")
  void registerSuccess() {
    given(userRepository.existsByEmail("user@copa.com")).willReturn(false);
    given(userRepository.save(any(User.class))).willReturn(userWithId(1L));

    User saved = userService.register("user@copa.com", "encoded-password", "코파");

    assertThat(saved.getId()).isEqualTo(1L);
    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("이미 존재하는 이메일이면 예외를 던지고 저장하지 않는다")
  void registerDuplicateEmail() {
    given(userRepository.existsByEmail("user@copa.com")).willReturn(true);

    assertThatThrownBy(() -> userService.register("user@copa.com", "encoded-password", "코파"))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("내 정보 조회 성공")
  void getProfileSuccess() {
    given(userRepository.findById(1L)).willReturn(Optional.of(userWithId(1L)));

    UserResponse response = userService.getProfile(1L);

    assertThat(response.getId()).isEqualTo(1L);
    assertThat(response.getEmail()).isEqualTo("user@copa.com");
  }

  @Test
  @DisplayName("사용자가 없으면 USER_NOT_FOUND 예외를 던진다")
  void getByIdNotFound() {
    given(userRepository.findById(99L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getProfile(99L))
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode").isEqualTo(ErrorCode.USER_NOT_FOUND);
  }
}
