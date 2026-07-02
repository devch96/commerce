# syntax=docker/dockerfile:1

# ---- build stage: Gradle Wrapper로 실행 가능한 bootJar 생성 ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 래퍼·의존성 정의를 먼저 복사해 의존성 다운로드 레이어를 캐시한다.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

# 소스 복사 후 bootJar 빌드. plain 아카이브(-plain.jar)는 제외하고 실행 가능 jar만 취한다.
COPY src src
RUN ./gradlew clean bootJar --no-daemon \
    && cp "$(ls build/libs/*.jar | grep -v plain)" app.jar

# ---- runtime stage: JRE만 담아 이미지 축소 ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]