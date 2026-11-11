package rw.shop.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<Map<String, Object>> details;

    protected ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    protected ApiException(ErrorCode errorCode, String message, List<Map<String, Object>> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<Map<String, Object>> getDetails() {
        return details;
    }

    public HttpStatus getStatus() {
        return errorCode.status();
    }

    public static List<Map<String, Object>> details(String field, String message) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("field", field, "message", message));
        return list;
    }
}
