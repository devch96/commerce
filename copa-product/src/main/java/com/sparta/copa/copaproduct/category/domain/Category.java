package com.sparta.copa.copaproduct.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// 무한 중첩 카테고리. 자식은 parentId로 부모를 가리킨다(parentId == null 이면 최상위).
@Entity
@Getter
@Table(name = "categories")
@EntityListeners(AuditingEntityListener.class)
@DynamicInsert
@DynamicUpdate
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(name = "parent_id")
  private Long parentId;

  @CreatedDate
  @Column(updatable = false)
  private LocalDateTime createdAt;

  @LastModifiedDate
  private LocalDateTime updatedAt;

  @Builder
  private Category(String name, Long parentId) {
    this.name = name;
    this.parentId = parentId;
  }

  public static Category create(String name, Long parentId) {
    return Category.builder()
        .name(name)
        .parentId(parentId)
        .build();
  }

  public void rename(String name) {
    this.name = name;
  }

  public void moveTo(Long parentId) {
    this.parentId = parentId;
  }
}
