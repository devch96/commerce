package com.sparta.copa.copaproduct.common.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.copa.copaproduct.product.domain.OptionDiscount;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;

/**
 * 옵션 할인 규칙 목록을 JSON 문자열 컬럼 하나로 저장하기 위한 컨버터.
 * 운영(MySQL)에선 JSON 컬럼, 테스트(H2)에선 VARCHAR로 매핑된다.
 */
@Converter
public class OptionDiscountListJsonConverter
    implements AttributeConverter<List<OptionDiscount>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<List<OptionDiscount>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(List<OptionDiscount> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(attribute);
    } catch (Exception e) {
      throw new IllegalArgumentException("옵션 할인을 직렬화하지 못했습니다.", e);
    }
  }

  @Override
  public List<OptionDiscount> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(dbData, TYPE);
    } catch (Exception e) {
      throw new IllegalArgumentException("옵션 할인을 역직렬화하지 못했습니다.", e);
    }
  }
}
