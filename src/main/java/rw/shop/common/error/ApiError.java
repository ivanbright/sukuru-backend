package rw.shop.common.error;

import java.util.List;
import java.util.Map;

public record ApiError(String code, String message, List<Map<String, Object>> details, String correlationId) {
}
