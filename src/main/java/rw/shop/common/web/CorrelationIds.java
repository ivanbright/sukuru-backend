package rw.shop.common.web;

import org.slf4j.MDC;

public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private CorrelationIds() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null ? "" : value;
    }
}
