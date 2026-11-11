package rw.shop.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import rw.shop.common.error.ApiError;
import rw.shop.common.error.ApiException;
import rw.shop.common.error.ErrorCode;

public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyFilter.class);

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotencyFilter(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !uri.startsWith("/api/v1/")
                || uri.startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            writeError(response, 400, ErrorCode.VALIDATION_ERROR, "Idempotency-Key header is required");
            return;
        }
        try {
            UUID.fromString(key);
        } catch (IllegalArgumentException ex) {
            writeError(response, 400, ErrorCode.VALIDATION_ERROR, "Idempotency-Key must be a UUID");
            return;
        }
        UUID businessId = TenantContext.businessId();
        if (businessId == null) {
            writeError(response, 401, ErrorCode.UNAUTHENTICATED, "Authentication is required");
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String requestHash = hash(cachedRequest);

        Optional<IdempotencyService.StoredResponse> stored;
        try {
            stored = idempotencyService.begin(businessId, key, requestHash);
        } catch (ApiException ex) {
            writeApiError(response, ex);
            return;
        }
        if (stored.isPresent()) {
            writeStored(response, stored.get());
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, wrapper);
        } catch (ApiException ex) {
            wrapper.resetBuffer();
            writeApiError(wrapper, ex);
            complete(businessId, key, wrapper);
            wrapper.copyBodyToResponse();
            return;
        } catch (Exception ex) {
            log.error("Unhandled error while processing idempotent request", ex);
            wrapper.resetBuffer();
            writeError(wrapper, 500, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
            complete(businessId, key, wrapper);
            wrapper.copyBodyToResponse();
            return;
        }

        complete(businessId, key, wrapper);
        wrapper.copyBodyToResponse();
    }

    private void complete(UUID businessId, String key, ContentCachingResponseWrapper wrapper) {
        String body = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        try {
            idempotencyService.complete(businessId, key, wrapper.getStatus(), body.isEmpty() ? null : body);
        } catch (RuntimeException ex) {
            log.error("Could not store idempotency response", ex);
        }
    }

    private void writeStored(HttpServletResponse response, IdempotencyService.StoredResponse stored) throws IOException {
        response.setStatus(stored.statusCode());
        if (stored.body() != null && !stored.body().isBlank()) {
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(stored.body().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeApiError(HttpServletResponse response, ApiException ex) throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                ex.getErrorCode().name(), ex.getMessage(), ex.getDetails(), CorrelationIds.current()));
    }

    private void writeError(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(
                code.name(), message, List.of(Map.of("field", "Idempotency-Key", "message", message)),
                CorrelationIds.current()));
    }

    private String hash(CachedBodyHttpServletRequest request) {
        StringBuilder source = new StringBuilder();
        source.append(request.getMethod()).append('\n');
        source.append(request.getRequestURI());
        if (request.getQueryString() != null) {
            source.append('?').append(request.getQueryString());
        }
        source.append('\n');
        source.append(new String(request.getBody(), StandardCharsets.UTF_8));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
