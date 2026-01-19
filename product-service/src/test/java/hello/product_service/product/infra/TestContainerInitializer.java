package hello.product_service.product.infra;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

// GenericContainer를 사용하여 Redis 컨테이너 정의 (Redis Docker 이미지 사용)
@Testcontainers
public class TestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    // 💡 Redis 컨테이너 정의: static으로 선언하여 테스트 간 공유 (컨테이너 재사용)
    @Container
    public static GenericContainer<?> redisContainer = new GenericContainer<>("redis:latest")
        .withExposedPorts(6379);

    // 💡 RabbitMQ 컨테이너 추가
    @Container
    public static GenericContainer<?> rabbitMqContainer = new GenericContainer<>("rabbitmq:3-management")
        .withExposedPorts(5672, 15672);
    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        // 컨테이너가 실행되지 않았다면 실행
        if (!redisContainer.isRunning()) {
            redisContainer.start();
        }

        if (!rabbitMqContainer.isRunning()) {
            rabbitMqContainer.start();
        }

        // 컨테이너가 동적으로 할당한 포트 가져오기
        String host = redisContainer.getHost();
        Integer port = redisContainer.getMappedPort(6379);

        // 💡 RabbitMQ 연결 정보 추출
        String rabbitHost = rabbitMqContainer.getHost();
        Integer rabbitPort = rabbitMqContainer.getMappedPort(5672);

        // redis 연결정보 주입
        // 이렇게 하면 application.yml/properties에 설정된 값보다 우선순위를 가짐
        Map<String, Object> map = Map.of(
            "spring.data.redis.host", host,
            "spring.data.redis.port", port,
            // 💡 RabbitMQ 프로퍼티 추가 주입
            "spring.rabbitmq.host", rabbitHost,
            "spring.rabbitmq.port", rabbitPort
        );

        MapPropertySource propertySource = new MapPropertySource("testcontainers", map);

        applicationContext.getEnvironment()
            .getPropertySources()
            .addFirst(propertySource);
    }
}
