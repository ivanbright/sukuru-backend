package rw.shop.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import rw.shop.common.error.ValidationException;

class QuantityTest {

    @Test
    void parsesAndFormatsThreeDecimals() {
        assertThat(Quantity.format(Quantity.parse("2", "quantity"))).isEqualTo("2.000");
        assertThat(Quantity.format(Quantity.parse("2.5", "quantity"))).isEqualTo("2.500");
    }

    @Test
    void rejectsMoreThanThreeDecimals() {
        assertThatThrownBy(() -> Quantity.parse("2.0001", "quantity"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void rejectsZeroWhenPositiveRequired() {
        assertThatThrownBy(() -> Quantity.requirePositive(BigDecimal.ZERO, "quantity"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Quantity.requireNonZero(BigDecimal.ZERO, "quantity"))
                .isInstanceOf(ValidationException.class);
    }
}
