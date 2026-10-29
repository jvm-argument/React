package com.g4vrk.react.inference;

import java.math.BigDecimal;

/** Decimal presentation without changing the value used by inference or history. */
public final class ProbabilityFormatter {
    private ProbabilityFormatter() {
    }

    public static String format(double probability) {
        return BigDecimal.valueOf(probability).stripTrailingZeros().toPlainString();
    }
}
