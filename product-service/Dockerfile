# 베이스 이미지 설정 (Java 17 사용 시)
FROM eclipse-temurin:17-jdk-focal

# 작업 디렉토리 설정
WORKDIR /app

# 빌드된 JAR 파일을 컨테이너 내부로 복사
# build/libs/ 폴더에 생성된 jar 파일 중 -plain이 붙지 않은 파일을 복사합니다.
ARG JAR_FILE=build/libs/*.jar
COPY ${JAR_FILE} app.jar

# 컨테이너 실행 시 자바 명령어로 실행
ENTRYPOINT ["java", "-jar", "app.jar"]

# 5. 상품 서버 포트 노출
EXPOSE 8081