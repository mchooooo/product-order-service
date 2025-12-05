package hello.orders_service.order.saga;

import hello.orders_service.order.domain.Order;
import hello.orders_service.order.service.OrderService;
import hello.orders_service.saga.SagaErrorType;
import hello.orders_service.saga.SagaException;
import hello.orders_service.saga.SagaStep;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@Slf4j
class OrderSagaOrchestratorTest {

    @Mock
    OrderService orderService;

    static class DummyStep implements SagaStep<OrderSagaContext> {

        private final String name;
        private final List<String> executeLog;    // 실행 순서 기록
        private final List<String> compensateLog; // 보상 순서 기록
        private final SagaException toThrow;      // execute 중 던질 예외 (null이면 정상)

        public DummyStep(String name, List<String> executeLog, List<String> compensateLog, SagaException toThrow) {
            this.name = name;
            this.executeLog = executeLog;
            this.compensateLog = compensateLog;
            this.toThrow = toThrow;
        }

        @Override
        public void execute(OrderSagaContext ctx) {
            log.info("현재 실행 중: {}", name);
            executeLog.add(name);
            if (toThrow != null) {
                throw toThrow;
            }
        }

        @Override
        public void compensate(OrderSagaContext ctx) {
            log.info("보상 진행 중: {}", name);
            compensateLog.add(name);
        }

        static DummyStep normal(String name, List<String> exec, List<String> comp) {
            return new DummyStep(name, exec, comp, null);
        }

        static DummyStep throwing(String name, List<String> exec, List<String> comp, SagaErrorType type) {
            return new DummyStep(name, exec, comp, new SagaException(type, name + " boom", null));
        }
    }

    @Test
    void 모든_step성공시_exec만_순서대로_호출하고_compensate는_호출하지_않는다() {
        // given
        List<String> execLog = new ArrayList<>();
        List<String> compLog = new ArrayList<>();

        SagaStep<OrderSagaContext> s1 = DummyStep.normal("s1", execLog, compLog);
        SagaStep<OrderSagaContext> s2 = DummyStep.normal("s2", execLog, compLog);

        List<SagaStep<OrderSagaContext>> steps = List.of(s1, s2);

        OrderSagaContext ctx = new OrderSagaContext();
        Order expected = new Order();
        ctx.setOrder(expected);

        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(orderService, null, null, null, null, null);

        // when
        Order result = orchestrator.runSaga(steps, ctx);

        // then
        assertThat(execLog).containsExactly("s1", "s2");
        assertThat(compLog).isEmpty();
        assertThat(result).isSameAs(expected);
    }

    @Test
    void 중간_step에서_COMPENSATE_발생시_이전_step만_역순_compensate된다() {
        // given
        List<String> execLog = new ArrayList<>();
        List<String> compLog = new ArrayList<>();

        SagaStep<OrderSagaContext> s1 = DummyStep.normal("s1", execLog, compLog);
        SagaStep<OrderSagaContext> s2 = DummyStep.throwing("s2", execLog, compLog, SagaErrorType.COMPENSATE);
        SagaStep<OrderSagaContext> s3 = DummyStep.normal("s3", execLog, compLog);

        List<SagaStep<OrderSagaContext>> steps = List.of(s1, s2, s3);
        OrderSagaContext ctx = new OrderSagaContext();

        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(orderService, null, null, null, null, null);

        // when
        Order result = orchestrator.runSaga(steps, ctx);

        // then
        // 실행은 s1, s2 까지만 되고 s3는 실행 안 됨
        assertThat(execLog).containsExactly("s1", "s2");
        // 보상은 s1만 (역순) 호출
        assertThat(compLog).containsExactly("s1");
    }

    @Test
    void BUSINESS_에러면_compensate_호출되지_않고_ctx_order_그대로_리턴된다() {
        // given
        List<String> execLog = new ArrayList<>();
        List<String> compLog = new ArrayList<>();

        SagaStep<OrderSagaContext> s1 = DummyStep.normal("s1", execLog, compLog);
        SagaStep<OrderSagaContext> s2 = DummyStep.throwing("s2", execLog, compLog, SagaErrorType.BUSINESS);

        List<SagaStep<OrderSagaContext>> steps = List.of(s1, s2);
        OrderSagaContext ctx = new OrderSagaContext();

        Order original = new Order();
        ctx.setOrder(original);

        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(orderService, null, null, null, null, null);

        // when
        Order result = orchestrator.runSaga(steps, ctx);

        // then
        assertThat(execLog).containsExactly("s1", "s2");
        assertThat(compLog).isEmpty();
        assertThat(result).isSameAs(original);
    }

    @Test
    void RETRYABLE_에러면_그대로_던지고_compensate는_호출되지_않는다() {
        // given
        List<String> execLog = new ArrayList<>();
        List<String> compLog = new ArrayList<>();

        SagaStep<OrderSagaContext> s1 = DummyStep.normal("s1", execLog, compLog);
        SagaStep<OrderSagaContext> s2 = DummyStep.throwing("s2", execLog, compLog, SagaErrorType.RETRYABLE);

        List<SagaStep<OrderSagaContext>> steps = List.of(s1, s2);
        OrderSagaContext ctx = new OrderSagaContext();

        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(orderService, null,null, null, null, null);

        // when
        SagaException ex = assertThrows(
            SagaException.class,
            () -> orchestrator.runSaga(steps, ctx)
        );

        // then
        assertThat(ex.getType()).isEqualTo(SagaErrorType.RETRYABLE);
        assertThat(execLog).containsExactly("s1", "s2");
        assertThat(compLog).isEmpty();
    }
}