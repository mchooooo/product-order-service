package hello.product_service.product.service;

import hello.product_service.product.domain.*;
import hello.product_service.product.exception.InsufficientStockException;
import hello.product_service.product.model.StockResult;
import hello.product_service.product.repository.IdempotencyRepository;
import hello.product_service.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InventoryService {
    private final StockLedgerService stockLedgerService;
    private final IdempotencyRepository idempotencyRepository;
    private final ProductRepository productRepository;

    /**
     * decreaseByOrder V1
     * update 쿼리에서 (WHERE p.id=:productId AND p.stock >= :qty) 조건을 통해 갱신과 검증을 한 번에 수행 했으나 요청이 많아서 하나의 아이템에 다른 멱등성 키를 가지고 요청한 경우 방어 가능한가?
     * 트래픽이 많이 몰릴 것으로 예상되는 상품은 데이터베이스의 부하를 덜기 위한 Redis 도입
     */
    public StockResult decreaseByOrder(Long productId, Long orderId, int quantity, String requestId) {
        // 요청 아이디가 신규 값인지 조회
        IdempotencyRecord cached = idempotencyRepository.findByRequestId(requestId).orElse(null);

        if (cached != null) { // 널이 아니면 기존에 존재
            return StockResult.create(cached);
        }

        // 차감 시작 (비즈니스 로직)
        // todo : 동시 요청, 요청이 많을 경우 해결 법?,
        // 힌트 : 레디스 도입
        int updated = productRepository.decrement(productId, quantity);
        StockResult stockResult = null;
        Product updatedProduct = productRepository.findById(productId).orElseThrow();

        if (updated == 1) { // 차감 성공
            stockResult = new StockResult(true, updatedProduct.getStock(), "OK");
        } else { // 차감 실패
            stockResult = new StockResult(false, null, "INSUFFICIENT_STOCK");
        }

        // 멱등성 저장 (경합 시 재조회)
        try {
            IdempotencyStatus status = stockResult.isSuccess() ? IdempotencyStatus.SUCCESS : IdempotencyStatus.FAIL;
            IdempotencyRecord idempotencyRecord = IdempotencyRecord.create(requestId, status, stockResult.getMessage(), stockResult.getRemainingStock());

            idempotencyRepository.save(idempotencyRecord);

        } catch (DataIntegrityViolationException e) { //유니크 제약 위반, 동시에 삽입될 경우
            // concurrent save → 기존 레코드 반환
            return idempotencyRepository.findByRequestId(requestId).map(StockResult::create).orElse(stockResult);
        }

        if (!stockResult.isSuccess()) {
            throw new InsufficientStockException(productId, updatedProduct.getStock());
        }

        return stockResult;
    }

    public StockResult increaseByOrder(Long productId, Long orderId, int quantity, String requestId) {
        // 요청 아이디가 신규 값인지 조회
        IdempotencyRecord cached = idempotencyRepository.findByRequestId(requestId).orElse(null);

        if (cached != null) { // 널이 아니면 기존에 존재
            return StockResult.create(cached);
        }

        //비즈니스 로직 -> 주문 취소로 인한 재고 증가
        productRepository.increment(productId, quantity);
        Product updatedProduct = productRepository.findById(productId).orElseThrow();

        StockResult stockResult = new StockResult(true, updatedProduct.getStock(), "OK");

        // 멱등성 저장, 경합 시 재조회
        try {
            IdempotencyStatus status = stockResult.isSuccess() ? IdempotencyStatus.SUCCESS : IdempotencyStatus.FAIL;
            IdempotencyRecord idempotencyRecord = IdempotencyRecord.create(requestId, status, stockResult.getMessage(), stockResult.getRemainingStock());
            idempotencyRepository.save(idempotencyRecord);

        } catch (DataIntegrityViolationException e) { //유니크 제약 위반

            return idempotencyRepository.findByRequestId(requestId).map(StockResult::create).orElse(stockResult);
        }

        return stockResult;
    }

    /**
     * decreaseByOrder V2
     * 재고 감소에서 비관적 락 도입
     * @Lock(LockModeType.PESSIMISTIC_WRITE)
     * 메서드 흐름
     *  멱등성 검사 -> 존재하면 기존 값 리턴
     *           -> 존재하지 않으면 상품에 락 걸고 검색 (SELECT ... FOR UPDATE) -> 재고 감소 실패 시 InsufficientStockException 던짐
     *                                                                  -> 재고 감소 성공 시 재고 원장, 멱등성 저장 후 StockResult 리턴
     *
     * 고도화 아이디어 : 현재 코드는 모든 상품에 락이 걸림, 인기 있는 상품만 락을 걸 수 없을까?
     *
     * DB 락 철회 :
     * 인기 상품처럼 재고 감소 트래픽이 집중되는 환경에서 디비 락을 걸면 오히려 성능이 저하된다.
     * 데이터베이스의 부하를 덜기 위해 Redis 도입 예정
     */
    @Deprecated
    public StockResult decreaseByOrderV2(Long productId, Long orderId, int quantity, String requestId) {
        log.info("decrease by order call, product id = {}, order id = {}, quantity = {}, request id = {}", productId, orderId, quantity, requestId);
        IdempotencyRecord cached = idempotencyRepository.findByRequestId(requestId).orElse(null);

        if (cached != null) { //널이 아니면 기존에 존재
            return StockResult.create(cached);
        }

        // 비즈니스 로직 시작
        // 락 걸고 검색
        Product findItem = productRepository.findForUpdateWithTimeout(productId);
        // 재고 감소
        findItem.decreaseStock(quantity); // 재고 부족 익셉션이 발생할 수 있음.
        StockResult stockResult = new StockResult(true, findItem.getStock(), "OK");
        log.info("decrease stock result = {}", stockResult);
        // 재고 원장 저장 (성공한 아이템만)
        stockLedgerService.save(productId, Direction.OUT, quantity, orderId, requestId);
        // 멱등성 저장
        try {
            IdempotencyRecord idempotencyRecord = IdempotencyRecord.create(requestId, IdempotencyStatus.SUCCESS, stockResult.getMessage(), stockResult.getRemainingStock());
            idempotencyRepository.save(idempotencyRecord);
        } catch (DataIntegrityViolationException e) { //유니크 제약 위반, 동시에 삽입될 경우
            // 드물지만 동일한 멱등성 키를 가지고 요청이 들어온 경우
            // concurrent save -> 기존 레코드 반환
            return idempotencyRepository.findByRequestId(requestId).map(StockResult::create).orElse(stockResult);
        }


        return stockResult;
    }

}
