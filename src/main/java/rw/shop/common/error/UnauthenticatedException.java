package rw.shop.common.error;

public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
