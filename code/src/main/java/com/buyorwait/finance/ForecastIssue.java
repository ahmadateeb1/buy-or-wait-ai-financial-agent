package com.buyorwait.finance;

import java.util.Optional;

public record ForecastIssue(String code, String message, Optional<String> eventId) {
    public ForecastIssue {
        eventId = eventId == null ? Optional.empty() : eventId;
    }

    public static ForecastIssue forEvent(String code, String message, String eventId) {
        return new ForecastIssue(code, message, Optional.ofNullable(eventId));
    }
}
