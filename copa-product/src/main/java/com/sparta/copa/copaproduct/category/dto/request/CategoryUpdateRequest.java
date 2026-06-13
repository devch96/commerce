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
public class CategoryUpdateRequest {

  @NotBlank
  @Size(max = 50)
  private String name;

  // 지정 시 해당 부모로 이동(null이면 최상위로 이동).
  private Long parentId;
}
