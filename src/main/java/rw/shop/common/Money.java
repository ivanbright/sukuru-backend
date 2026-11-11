package rw.shop.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import rw.shop.common.error.ValidationException;

public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Money() {
    }

    public static BigDecimal parse(String value, String field) {
        BigDecimal parsed = parseDecimal(value, field);
        if (parsed.scale() > SCALE) {
            throw new ValidationException(field, "must have at most " + SCALE + " decimal places");
        }
        return parsed.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal multiply(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(SCALE, ROUNDING);
    }

    public static String format(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING).toPlainString();
    }

    public static void requireNonNegative(BigDecimal value, String field) {
        if (value.signum() < 0) {
            throw new ValidationException(field, "must be at or above 0");
        }
    }

    public static void requirePositive(BigDecimal value, String field) {
        if (value.signum() <= 0) {
            throw new ValidationException(field, "must be greater than 0");
        }
    }

    private static BigDecimal parseDecimal(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, "is required");
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(field, "must be a decimal number");
        }
    }

    public static UUID requireUuid(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, "is required");
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(field, "must be a valid UUID");
        }
    }
}
