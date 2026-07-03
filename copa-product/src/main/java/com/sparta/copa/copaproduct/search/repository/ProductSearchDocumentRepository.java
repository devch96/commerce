package com.sparta.copa.copaproduct.search.repository;

import com.sparta.copa.copaproduct.search.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Elasticsearch 상품 문서 저장소. 색인기가 upsert({@code save})·delete({@code deleteById})에 쓴다.
 * 복잡한 bool/range/집계 쿼리는 {@code ElasticsearchOperations}로 별도 수행한다.
 */
public interface ProductSearchDocumentRepository
    extends ElasticsearchRepository<ProductDocument, String> {
}