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
import jakarta.persistence.Version;
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

  @Column(nullable = false, length = 30)
  private String name;

  @Column(nullable = false, length = 20)
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserRole role;

  // 회원 탈퇴 시 데이터를 물리 삭제하지 않고 비활성화한다(주문 이력 등 참조 무결성 보존).
  @Column(nullable = false)
  private Boolean isActive;

  // 비밀번호 변경·등급 변경 등 동시 수정에 대한 낙관적 락.
  @Version
  private Long version;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @Builder
  private User(String email, String password, String name, String phone, UserRole role) {
    this.email = email;
    this.password = password;
    this.name = name;
    this.phone = phone;
    this.role = role;
    this.isActive = Boolean.TRUE;
  }

  public static User create(String email, String encodedPassword, String name, String phone) {
    return User.builder()
        .email(email)
        .password(encodedPassword)
        .name(name)
        .phone(phone)
        .role(UserRole.USER)
        .build();
  }

  // 이메일은 식별자로 변경 불가. 이름·휴대폰만 수정한다.
  public void updateProfile(String name, String phone) {
    this.name = name;
    this.phone = phone;
  }

  public void changePassword(String encodedNewPassword) {
    this.password = encodedNewPassword;
  }

  public void withdraw() {
    this.isActive = Boolean.FALSE;
  }

  public void changeRole(UserRole role) {
    this.role = role;
  }
}
