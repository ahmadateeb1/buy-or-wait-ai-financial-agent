package com.buyorwait.decision;

import java.util.Locale;

public enum AffordabilityStatus {
    AFFORDABLE_NOW, AFFORDABLE_WITH_PLAN, AFFORDABLE_LATER, NOT_AFFORDABLE;
    public String csvValue() { return name().toLowerCase(Locale.ROOT); }
}
