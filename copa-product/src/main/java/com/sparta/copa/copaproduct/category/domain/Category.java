package com.sparta.copa.copaproduct.category.domain;

import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// 무한 중첩 카테고리. 자식은 parentId로 부모를 가리킨다(parentId == null 이면 최상위).
@Getter
@Document(collection = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

  @Id
  private String id;

  private String name;

  @Indexed
  private String parentId;

  @CreatedDate
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Category(String name, String parentId) {
    this.name = name;
    this.parentId = parentId;
  }

  public static Category create(String name, String parentId) {
    return Category.builder()
        .name(name)
        .parentId(parentId)
        .build();
  }

  public void rename(String name) {
    this.name = name;
  }

  public void moveTo(String parentId) {
    this.parentId = parentId;
  }
}
