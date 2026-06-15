package com.sparta.copa.copaproduct.product.domain;

import com.sparta.copa.copaproduct.common.exception.BusinessException;
import com.sparta.copa.copaproduct.common.exception.ErrorCode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 상품 옵션(무한 뎁스 JSON) 트리를 다루는 헬퍼.
 *
 * <p>트리는 차원(dimension) → 값(value)이 번갈아 중첩되며, leaf 값이 선언적 재고 수량이다.
 * <pre>
 * { "색상": { "네이비": { "사이즈": { "M": 10, "L": 5 } },
 *            "블랙":   { "사이즈": { "M": 20 } } } }
 * </pre>
 * 이를 leaf 경로(optionKey) → 재고로 평탄화한다: {@code 색상:네이비/사이즈:M → 10}.
 * 차원/값 이름에는 구분자 {@code :} 와 {@code /} 를 쓸 수 없다.
 */
public final class ProductOptions {

  private static final String SEGMENT_DELIMITER = "/";
  private static final String PAIR_DELIMITER = ":";

  private ProductOptions() {
  }

  // 옵션 트리를 검증하며 optionKey → 재고 맵으로 평탄화한다(삽입 순서 보존).
  public static Map<String, Integer> flatten(Map<String, Object> options) {
    Map<String, Integer> leaves = new LinkedHashMap<>();
    if (options == null || options.isEmpty()) {
      return leaves;
    }
    walk(options, "", leaves);
    if (leaves.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION);
    }
    return leaves;
  }

  // 옵션 트리의 모든 leaf 재고 합계(상품의 집계 재고).
  public static int totalStock(Map<String, Object> options) {
    return flatten(options).values().stream().mapToInt(Integer::intValue).sum();
  }

  @SuppressWarnings("unchecked")
  private static void walk(Map<String, Object> dimensionNode, String prefix,
      Map<String, Integer> leaves) {
    if (dimensionNode.isEmpty()) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION);
    }
    for (Map.Entry<String, Object> dimension : dimensionNode.entrySet()) {
      validateName(dimension.getKey());
      if (!(dimension.getValue() instanceof Map<?, ?> valueNode) || valueNode.isEmpty()) {
        throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION);
      }
      for (Map.Entry<?, ?> value : valueNode.entrySet()) {
        String valueName = String.valueOf(value.getKey());
        validateName(valueName);
        String key = prefix.isEmpty()
            ? dimension.getKey() + PAIR_DELIMITER + valueName
            : prefix + SEGMENT_DELIMITER + dimension.getKey() + PAIR_DELIMITER + valueName;
        Object child = value.getValue();
        if (child instanceof Map) {
          walk((Map<String, Object>) child, key, leaves);
        } else {
          leaves.put(key, toStock(child));
        }
      }
    }
  }

  private static int toStock(Object leaf) {
    if (!(leaf instanceof Number number)) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION);
    }
    // 정수가 아니거나 음수면 재고로 부적합.
    if (number.doubleValue() != Math.floor(number.doubleValue()) || number.intValue() < 0) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION);
    }
    return number.intValue();
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()
        || name.contains(PAIR_DELIMITER) || name.contains(SEGMENT_DELIMITER)) {
      throw new BusinessException(ErrorCode.INVALID_PRODUCT_OPTION);
    }
  }
}
