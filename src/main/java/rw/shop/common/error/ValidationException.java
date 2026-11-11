package rw.shop.common.error;

import java.util.List;
import java.util.Map;

public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public ValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR, field + " " + message, ApiException.details(field, message));
    }

    public ValidationException(String message, List<Map<String, Object>> details) {
        super(ErrorCode.VALIDATION_ERROR, message, details);
    }
}
