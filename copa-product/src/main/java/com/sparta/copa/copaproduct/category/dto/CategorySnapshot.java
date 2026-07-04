package com.sparta.copa.copaproduct.category.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sparta.copa.copaproduct.category.domain.Category;
import lombok.Getter;

/**
 * 카테고리 캐시용 경량 스냅샷. 트리 조립·하위 트리 펼치기에 필요한 최소 필드(id·name·parentId)만 담아
 * JPA 엔티티 대신 캐시에 저장한다(엔티티 직렬화의 프록시·감사필드 문제 회피).
 */
@Getter
public class CategorySnapshot {

  private final Long id;
  private final String name;
  private final Long parentId;

  @JsonCreator
  public CategorySnapshot(
      @JsonProperty("id") Long id,
      @JsonProperty("name") String name,
      @JsonProperty("parentId") Long parentId) {
    this.id = id;
    this.name = name;
    this.parentId = parentId;
  }

  public static CategorySnapshot from(Category category) {
    return new CategorySnapshot(category.getId(), category.getName(), category.getParentId());
  }
}
