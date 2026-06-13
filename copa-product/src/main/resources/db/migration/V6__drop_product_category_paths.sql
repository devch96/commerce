-- 조상 클로저 저장 방식을 폐기하고, 검색 시점에 하위 트리를 펼쳐 조회한다.
-- product_categories(@ManyToMany 조인 테이블)는 그대로 재사용한다.
DROP TABLE product_category_paths;
