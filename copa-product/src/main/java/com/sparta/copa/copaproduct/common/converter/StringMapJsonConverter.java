package com.sparta.copa.copaproduct.common.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

/**
 * 상품의 유연 스펙(specs)을 값 테이블 대신 JSON 문자열 컬럼 하나로 저장하기 위한 컨버터.
 * 운영(MySQL)에선 JSON 컬럼, 테스트(H2)에선 VARCHAR로 매핑된다(컨버터 출력이 String이라 둘 다 호환).
 */
@Converter
public class StringMapJsonConverter implements AttributeConverter<Map<String, String>, String> {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, String>> TYPE = new TypeReference<>() {};

  @Override
  public String convertToDatabaseColumn(Map<String, String> attribute) {
    if (attribute == null || attribute.isEmpty()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.writeValueAsString(attribute);
    } catch (Exception e) {
      throw new IllegalArgumentException("상품 스펙을 직렬화하지 못했습니다.", e);
    }
  }

  @Override
  public Map<String, String> convertToEntityAttribute(String dbData) {
    if (dbData == null || dbData.isBlank()) {
      return Map.of();
    }
    try {
      return OBJECT_MAPPER.readValue(dbData, TYPE);
    } catch (Exception e) {
      throw new IllegalArgumentException("상품 스펙을 역직렬화하지 못했습니다.", e);
    }
  }
}
