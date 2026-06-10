package com.sparta.copa.copa.user.service;

import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.user.domain.User;
import com.sparta.copa.copa.user.domain.UserRole;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public User register(String email, String encodedPassword, String name, String phone) {
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
    return userRepository.save(User.create(email, encodedPassword, name, phone));
  }

  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return userRepository.findByEmail(email);
  }

  @Transactional(readOnly = true)
  public UserResponse getProfile(Long userId) {
    return UserResponse.from(getById(userId));
  }

  @Transactional
  public UserResponse updateProfile(Long userId, String name, String phone) {
    User user = getById(userId);
    user.updateProfile(name, phone);
    return UserResponse.from(user);
  }

  @Transactional
  public void changePassword(Long userId, String currentPassword, String newPassword) {
    User user = getById(userId);
    if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_PASSWORD);
    }
    if (passwordEncoder.matches(newPassword, user.getPassword())) {
      throw new BusinessException(ErrorCode.SAME_AS_OLD_PASSWORD);
    }
    user.changePassword(passwordEncoder.encode(newPassword));
  }

  // 운영자(ADMIN)가 회원 등급을 변경한다. (예: 일반 회원을 판매자로 승격)
  @Transactional
  public UserResponse changeRole(Long userId, UserRole role) {
    User user = getById(userId);
    user.changeRole(role);
    return UserResponse.from(user);
  }

  @Transactional
  public void withdraw(Long userId, String password) {
    User user = getById(userId);
    if (!passwordEncoder.matches(password, user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_PASSWORD);
    }
    user.withdraw();
  }

  @Transactional(readOnly = true)
  public User getById(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }
}
