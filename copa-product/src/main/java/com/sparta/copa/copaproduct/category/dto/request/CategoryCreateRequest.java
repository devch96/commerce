package com.sparta.copa.copaproduct.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CategoryCreateRequest {

  @NotBlank
  @Size(max = 50)
  private String name;

  // null이면 최상위 카테고리.
  private String parentId;
}
