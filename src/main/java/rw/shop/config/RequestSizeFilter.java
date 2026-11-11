package rw.shop.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import rw.shop.common.error.ApiError;
import rw.shop.common.error.ErrorCode;
import rw.shop.common.web.CorrelationIds;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeFilter extends OncePerRequestFilter {

    private static final long MAX_BYTES = 1024L * 1024L;

    private final ObjectMapper objectMapper;

    public RequestSizeFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            response.setStatus(400);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), new ApiError(
                    ErrorCode.VALIDATION_ERROR.name(),
                    "Request body must not exceed 1 MB",
                    List.of(Map.of("field", "body", "message", "must not exceed 1 MB")),
                    CorrelationIds.current()));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
