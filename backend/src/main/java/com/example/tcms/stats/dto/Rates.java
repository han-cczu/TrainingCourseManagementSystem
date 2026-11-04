package com.example.tcms.stats.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 比率计算：保留 4 位小数（前端展示为百分比保留 1 位）；分母为 0 时返回 null 而不是 0。 */
final class Rates {

    private Rates() {
    }

    static Double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
