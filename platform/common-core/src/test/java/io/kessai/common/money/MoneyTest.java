package io.kessai.common.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Nested
    @DisplayName("arithmetic is exact")
    class Arithmetic {

        @Test
        void adds_without_drift_over_many_operations() {
            // The classic float failure: 0.1 added ten times is not 1.0 in IEEE-754.
            // In minor units it is simply 10 + 10 + ... which is exact by construction.
            Money total = Money.zero(Currency.USD);
            for (int i = 0; i < 10; i++) {
                total = total.plus(Money.of(10, Currency.USD)); // 10 cents
            }
            assertThat(total.minorUnits()).isEqualTo(100L);
            assertThat(total.toMajorUnits().toPlainString()).isEqualTo("1.00");
        }

        @Test
        void subtracts_and_negates() {
            Money balance = Money.yen(1_000);
            assertThat(balance.minus(Money.yen(300))).isEqualTo(Money.yen(700));
            assertThat(Money.yen(700).negated()).isEqualTo(Money.yen(-700));
        }

        @Test
        void overflow_throws_rather_than_wrapping_a_balance_negative() {
            Money huge = Money.yen(Long.MAX_VALUE);
            assertThatThrownBy(() -> huge.plus(Money.yen(1)))
                    .isInstanceOf(ArithmeticException.class);
        }
    }

    @Nested
    @DisplayName("currency is enforced, never coerced")
    class CurrencySafety {

        @Test
        void combining_different_currencies_is_rejected() {
            assertThatThrownBy(() -> Money.yen(100).plus(Money.of(100, Currency.USD)))
                    .isInstanceOf(CurrencyMismatchException.class)
                    .hasMessageContaining("explicit conversion");
        }

        @Test
        void comparison_across_currencies_is_rejected() {
            assertThatThrownBy(() -> Money.yen(100).compareTo(Money.of(100, Currency.USD)))
                    .isInstanceOf(CurrencyMismatchException.class);
        }
    }

    @Nested
    @DisplayName("rendering respects currency scale")
    class Rendering {

        @Test
        void jpy_has_no_decimal_places() {
            assertThat(Money.yen(1_500).toString()).isEqualTo("1500 JPY");
        }

        @Test
        void usd_has_two_decimal_places() {
            assertThat(Money.of(1_500, Currency.USD).toString()).isEqualTo("15.00 USD");
        }
    }
}
