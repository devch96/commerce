package com.sparta.copa.copaproduct.search.indexer;

import com.sparta.copa.copaproduct.search.document.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 시작 시 Elasticsearch 'products' 인덱스를 명시 매핑({@link ProductDocument}의 {@code @Field})으로
 * 생성한다. 색인기가 문서를 먼저 저장하면 ES가 동적 매핑으로 인덱스를 만들어 {@code status}가 text로
 * 잡히는 등 term 필터가 어긋나므로, 매핑을 선제적으로 고정한다.
 *
 * <p>ES 미가용(테스트 등)에서는 색인기 플래그로 비활성화되고, 연결 실패는 삼켜 앱 기동을 막지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "copa.search.indexer.enabled", havingValue = "true", matchIfMissing = true)
public class ProductSearchIndexInitializer implements ApplicationRunner {

  private final ElasticsearchOperations elasticsearchOperations;

  @Override
  public void run(ApplicationArguments args) {
    try {
      IndexOperations indexOperations = elasticsearchOperations.indexOps(ProductDocument.class);
      if (!indexOperations.exists()) {
        indexOperations.createWithMapping();
        log.info("Elasticsearch 'products' 인덱스를 명시 매핑으로 생성했습니다.");
      }
    } catch (Exception e) {
      // ES가 아직 안 떠 있어도 앱은 기동시킨다(검색/색인 첫 사용 시 재시도가 필요).
      log.warn("Elasticsearch 인덱스 초기화를 건너뜁니다(연결 실패): {}", e.getMessage());
    }
  }
}