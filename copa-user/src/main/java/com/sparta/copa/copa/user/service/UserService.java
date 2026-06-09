package com.sparta.copa.copa.user.service;

import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.user.domain.User;
import com.sparta.copa.copa.user.dto.response.UserResponse;
import com.sparta.copa.copa.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  @Transactional
  public User register(String email, String encodedPassword, String name) {
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }
    return userRepository.save(User.create(email, encodedPassword, name));
  }

  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return userRepository.findByEmail(email);
  }

  @Transactional(readOnly = true)
  public UserResponse getProfile(Long userId) {
    return UserResponse.from(getById(userId));
  }

  @Transactional(readOnly = true)
  public User getById(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }
}
