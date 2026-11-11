package rw.shop.common.error;

public class IdempotencyKeyReusedException extends ApiException {

    public IdempotencyKeyReusedException() {
        super(ErrorCode.IDEMPOTENCY_KEY_REUSED, "Idempotency-Key was already used with a different request");
    }
}
