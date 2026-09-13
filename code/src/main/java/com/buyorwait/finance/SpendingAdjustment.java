package com.buyorwait.finance;

import java.math.BigDecimal;

public sealed interface SpendingAdjustment permits SpendingAdjustment.Stop, SpendingAdjustment.ReduceTo {
    String originEventId();

    record Stop(String originEventId) implements SpendingAdjustment {
    }

    record ReduceTo(String originEventId, BigDecimal newAmount) implements SpendingAdjustment {
        public ReduceTo {
            if (newAmount == null || newAmount.signum() < 0) throw new IllegalArgumentException("Reduced amount must be non-negative");
        }
    }
}
