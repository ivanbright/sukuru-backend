package rw.shop.common.error;

public class RateLimitedException extends ApiException {

    public RateLimitedException(String message) {
        super(ErrorCode.RATE_LIMITED, message);
    }
}
