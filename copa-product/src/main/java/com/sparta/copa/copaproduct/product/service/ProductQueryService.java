package com.sparta.copa.copaproduct.product.service;

import com.sparta.copa.copaproduct.product.domain.Product;
import com.sparta.copa.copaproduct.product.repository.ProductRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인(장바구니 등)이 상품을 조회할 때 쓰는 경계(boundary).
 * 상품 리포지토리를 직접 의존하지 않게 해, 나중에 장바구니가 별도 서비스로 분리되면
 * 이 자리만 원격 호출로 바꾸면 되도록 한다.
 */
@Service
@RequiredArgsConstructor
public class ProductQueryService {

  private final ProductRepository productRepository;

  @Transactional(readOnly = true)
  public Optional<Product> findById(Long productId) {
    return productRepository.findById(productId);
  }

  @Transactional(readOnly = true)
  public List<Product> findAllByIds(Collection<Long> productIds) {
    return productRepository.findAllById(productIds);
  }
}
