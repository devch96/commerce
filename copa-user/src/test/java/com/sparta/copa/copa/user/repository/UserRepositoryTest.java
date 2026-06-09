package com.sparta.copa.copa.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.copa.copa.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class UserRepositoryTest {

  @Autowired
  private UserRepository userRepository;

  @BeforeEach
  void setUp() {
    userRepository.save(User.create("user@copa.com", "encoded-password", "코파"));
  }

  @Test
  @DisplayName("이메일로 사용자를 조회한다")
  void findByEmail() {
    assertThat(userRepository.findByEmail("user@copa.com"))
        .isPresent()
        .hasValueSatisfying(user -> assertThat(user.getName()).isEqualTo("코파"));
  }

  @Test
  @DisplayName("존재하지 않는 이메일은 빈 Optional을 반환한다")
  void findByEmailNotFound() {
    assertThat(userRepository.findByEmail("none@copa.com")).isEmpty();
  }

  @Test
  @DisplayName("이메일 존재 여부를 확인한다")
  void existsByEmail() {
    assertThat(userRepository.existsByEmail("user@copa.com")).isTrue();
    assertThat(userRepository.existsByEmail("none@copa.com")).isFalse();
  }
}
