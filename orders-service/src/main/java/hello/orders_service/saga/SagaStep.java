package hello.orders_service.saga;

public interface SagaStep <C> {
    void execute(C context);

    void compensate(C context);
}
