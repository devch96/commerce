package com.sparta.copa.copaproduct.product.domain;

import com.sparta.copa.copaproduct.common.enums.DiscountType;
import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

/**
 * 옵션 할인 규칙. 상품 서비스가 들고 있는 할인 모델(쿠폰은 별도 프로모션 서비스).
 *
 * <p>{@code optionKey}는 옵션 경로(예: {@code 색상:네이비/사이즈:M})를 가리킨다.
 * <ul>
 *   <li>전체 leaf 경로면 <b>옵션 조합별 할인</b>(예: {@code 색상:블랙/사이즈:L}).</li>
 *   <li>상위 prefix면 <b>옵션별(단일 옵션) 할인</b>(예: {@code 색상:블랙} → 블랙 모든 조합).</li>
 * </ul>
 * 한 leaf에 여러 규칙이 걸리면 가장 구체적인(경로가 가장 긴) 규칙이 이긴다(중복 정책).
 *
 * <p>JSON 컬럼/캐시 직렬화를 위해 Jackson 친화적으로 작성한다.
 */
@Getter
@Builder
@Jacksonized
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionDiscount {

  private String optionKey;
  private DiscountType discountType;
  private BigDecimal value;

  // optionKey가 이 규칙의 적용 대상인지(정확히 일치하거나, 세그먼트 경계의 하위 경로).
  public boolean matches(String leafKey) {
    return leafKey.equals(optionKey) || leafKey.startsWith(optionKey + "/");
  }

  // 더 긴 경로일수록 구체적 → 최장 일치 우선 선택에 사용.
  public int specificity() {
    return optionKey == null ? 0 : optionKey.length();
  }

  // 기준 가격에 이 할인을 적용한 최종 가격(0 미만으로 내려가지 않음).
  public BigDecimal applyTo(BigDecimal basePrice) {
    BigDecimal discounted = switch (discountType) {
      case AMOUNT -> basePrice.subtract(value);
      case RATE -> basePrice
          .multiply(BigDecimal.valueOf(100).subtract(value))
          .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    };
    return discounted.max(BigDecimal.ZERO);
  }

  public void validate() {
    if (optionKey == null || optionKey.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_OPTION_DISCOUNT);
    }
    if (discountType == null || value == null || value.compareTo(BigDecimal.ZERO) < 0) {
      throw new BusinessException(ErrorCode.INVALID_OPTION_DISCOUNT);
    }
    if (discountType == DiscountType.RATE && value.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new BusinessException(ErrorCode.INVALID_OPTION_DISCOUNT);
    }
  }
}
