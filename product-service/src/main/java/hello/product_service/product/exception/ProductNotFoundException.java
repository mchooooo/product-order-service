package hello.product_service.product.exception;

public class ProductNotFoundException extends ApiException {

    public ProductNotFoundException(Long id) {
        super(ErrorCode.PRODUCT_NOT_FOUND, "상품("+id+")를 찾을 수 없습니다.");
    }
}
