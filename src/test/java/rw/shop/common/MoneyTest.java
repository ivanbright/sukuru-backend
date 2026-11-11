package rw.shop.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import rw.shop.common.error.ValidationException;

class MoneyTest {

    @Test
    void parsesAndFormatsTwoDecimals() {
        assertThat(Money.format(Money.parse("1250", "amount"))).isEqualTo("1250.00");
        assertThat(Money.format(Money.parse("10.5", "amount"))).isEqualTo("10.50");
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThatThrownBy(() -> Money.parse("10.123", "amount"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsBlankAndInvalidValues() {
        assertThatThrownBy(() -> Money.parse(" ", "amount")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Money.parse("abc", "amount")).isInstanceOf(ValidationException.class);
    }

    @Test
    void multipliesAndRoundsHalfUp() {
        assertThat(Money.multiply(new BigDecimal("3.000"), new BigDecimal("1.005")))
                .isEqualByComparingTo("3.02");
    }

    @Test
    void enforcesSigns() {
        assertThatThrownBy(() -> Money.requirePositive(BigDecimal.ZERO, "amount"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Money.requireNonNegative(new BigDecimal("-1"), "amount"))
                .isInstanceOf(ValidationException.class);
    }
}
