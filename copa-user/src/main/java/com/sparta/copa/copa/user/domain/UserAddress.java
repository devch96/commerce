package com.sparta.copa.copa.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Getter
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_addresses")
public class UserAddress {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // UserAddress -> User 단방향 ManyToOne. FK(user_id)는 자식 테이블이 소유한다.
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 50)
  private String addressName;

  @Column(nullable = false, length = 50)
  private String receiverName;

  @Column(nullable = false, length = 20)
  private String zipcode;

  @Column(nullable = false)
  private String baseAddress;

  @Column(nullable = false)
  private String detailAddress;

  @Column(nullable = false)
  private Boolean isDefault;

  @Builder
  private UserAddress(User user, String addressName, String receiverName, String zipcode,
      String baseAddress, String detailAddress, Boolean isDefault) {
    this.user = user;
    this.addressName = addressName;
    this.receiverName = receiverName;
    this.zipcode = zipcode;
    this.baseAddress = baseAddress;
    this.detailAddress = detailAddress;
    this.isDefault = isDefault;
  }

  public static UserAddress create(User user, String addressName, String receiverName,
      String zipcode, String baseAddress, String detailAddress, boolean isDefault) {
    return UserAddress.builder()
        .user(user)
        .addressName(addressName)
        .receiverName(receiverName)
        .zipcode(zipcode)
        .baseAddress(baseAddress)
        .detailAddress(detailAddress)
        .isDefault(isDefault)
        .build();
  }

  public void update(String addressName, String receiverName, String zipcode, String baseAddress,
      String detailAddress) {
    this.addressName = addressName;
    this.receiverName = receiverName;
    this.zipcode = zipcode;
    this.baseAddress = baseAddress;
    this.detailAddress = detailAddress;
  }

  public void markAsDefault() {
    this.isDefault = Boolean.TRUE;
  }

  public void unmarkDefault() {
    this.isDefault = Boolean.FALSE;
  }
}