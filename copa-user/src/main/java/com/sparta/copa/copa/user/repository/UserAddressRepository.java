package com.sparta.copa.copa.user.repository;

import com.sparta.copa.copa.user.domain.UserAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

  List<UserAddress> findByUser_IdOrderByIdAsc(Long userId);

  Optional<UserAddress> findByIdAndUser_Id(Long id, Long userId);
}