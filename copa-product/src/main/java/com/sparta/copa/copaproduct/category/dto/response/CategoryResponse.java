package com.sparta.copa.copaproduct.category.dto.response;

import com.sparta.copa.copaproduct.category.domain.Category;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CategoryResponse {

  private final Long id;
  private final String name;
  private final Long parentId;
  private final List<CategoryResponse> children;

  public static CategoryResponse of(Category category, List<CategoryResponse> children) {
    return CategoryResponse.builder()
        .id(category.getId())
        .name(category.getName())
        .parentId(category.getParentId())
        .children(children)
        .build();
  }
}
