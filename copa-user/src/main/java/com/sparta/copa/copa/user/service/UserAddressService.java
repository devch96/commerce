package com.sparta.copa.copa.user.service;

import com.sparta.copa.copa.common.exception.BusinessException;
import com.sparta.copa.copa.common.exception.ErrorCode;
import com.sparta.copa.copa.user.domain.User;
import com.sparta.copa.copa.user.domain.UserAddress;
import com.sparta.copa.copa.user.dto.request.UserAddressRequest;
import com.sparta.copa.copa.user.dto.response.UserAddressResponse;
import com.sparta.copa.copa.user.repository.UserAddressRepository;
import com.sparta.copa.copa.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAddressService {

  private static final int MAX_ADDRESS_COUNT = 5;

  private final UserRepository userRepository;
  private final UserAddressRepository userAddressRepository;

  @Transactional
  public UserAddressResponse addAddress(Long userId, UserAddressRequest request) {
    User user = getUser(userId);
    List<UserAddress> existing = userAddressRepository.findByUser_IdOrderByIdAsc(userId);
    if (existing.size() >= MAX_ADDRESS_COUNT) {
      throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED);
    }

    // 첫 주소이거나 기본 배송지로 요청되면 기존 기본 배송지를 해제하고 이 주소를 기본으로 둔다.
    boolean makeDefault = existing.isEmpty() || Boolean.TRUE.equals(request.getIsDefault());
    if (makeDefault) {
      clearDefault(existing);
    }

    UserAddress address = userAddressRepository.save(UserAddress.create(
        user,
        request.getAddressName(),
        request.getReceiverName(),
        request.getZipcode(),
        request.getBaseAddress(),
        request.getDetailAddress(),
        makeDefault));
    return UserAddressResponse.from(address);
  }

  @Transactional(readOnly = true)
  public List<UserAddressResponse> getAddresses(Long userId) {
    return userAddressRepository.findByUser_IdOrderByIdAsc(userId).stream()
        .map(UserAddressResponse::from)
        .toList();
  }

  @Transactional
  public UserAddressResponse updateAddress(Long userId, Long addressId, UserAddressRequest request) {
    UserAddress address = getOwnedAddress(userId, addressId);
    address.update(
        request.getAddressName(),
        request.getReceiverName(),
        request.getZipcode(),
        request.getBaseAddress(),
        request.getDetailAddress());
    return UserAddressResponse.from(address);
  }

  @Transactional
  public void changeDefaultAddress(Long userId, Long addressId) {
    List<UserAddress> addresses = userAddressRepository.findByUser_IdOrderByIdAsc(userId);
    UserAddress target = addresses.stream()
        .filter(address -> address.getId().equals(addressId))
        .findFirst()
        .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
    // 기본 배송지는 회원당 1개여야 한다. 기존 기본만 해제하지 않고 전체를 해제해, 데이터가
    // 깨져 기본이 2개 이상인 경우에도 불변식을 복구한다. (false->false는 변경 감지에서 제외돼 UPDATE 미발생)
    clearDefault(addresses);
    target.markAsDefault();
  }

  @Transactional
  public void deleteAddress(Long userId, Long addressId) {
    UserAddress target = getOwnedAddress(userId, addressId);
    boolean wasDefault = Boolean.TRUE.equals(target.getIsDefault());
    userAddressRepository.delete(target);

    // 기본 배송지를 삭제하면 남은 주소 중 첫 번째를 기본으로 승격한다.
    if (wasDefault) {
      List<UserAddress> remaining = userAddressRepository.findByUser_IdOrderByIdAsc(userId);
      if (!remaining.isEmpty()) {
        remaining.getFirst().markAsDefault();
      }
    }
  }

  private void clearDefault(List<UserAddress> addresses) {
    for (UserAddress address : addresses) {
      address.unmarkDefault();
    }
  }

  private UserAddress getOwnedAddress(Long userId, Long addressId) {
    return userAddressRepository.findByIdAndUser_Id(addressId, userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.ADDRESS_NOT_FOUND));
  }

  private User getUser(Long userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
  }
}