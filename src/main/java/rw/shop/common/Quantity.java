package rw.shop.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import rw.shop.common.error.ValidationException;

public final class Quantity {

    public static final int SCALE = 3;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private Quantity() {
    }

    public static BigDecimal parse(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(field, "is required");
        }
        BigDecimal parsed;
        try {
            parsed = new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new ValidationException(field, "must be a decimal number");
        }
        if (parsed.scale() > SCALE) {
            throw new ValidationException(field, "must have at most " + SCALE + " decimal places");
        }
        return parsed.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static String format(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING).toPlainString();
    }

    public static void requirePositive(BigDecimal value, String field) {
        if (value.signum() <= 0) {
            throw new ValidationException(field, "must be greater than 0");
        }
    }

    public static void requireNonZero(BigDecimal value, String field) {
        if (value.signum() == 0) {
            throw new ValidationException(field, "must not be zero");
        }
    }
}
