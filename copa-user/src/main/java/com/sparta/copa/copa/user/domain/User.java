package com.sparta.copa.copa.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(name = "users",
    uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email"))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  // BCrypt 단방향 해시만 저장한다. 평문 비밀번호는 절대 영속화하지 않는다.
  @Column(nullable = false)
  private String password;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private User(String email, String password, String name, UserRole role) {
    this.email = email;
    this.password = password;
    this.name = name;
    this.role = role;
  }

  public static User create(String email, String encodedPassword, String name) {
    return User.builder()
        .email(email)
        .password(encodedPassword)
        .name(name)
        .role(UserRole.USER)
        .build();
  }
}
