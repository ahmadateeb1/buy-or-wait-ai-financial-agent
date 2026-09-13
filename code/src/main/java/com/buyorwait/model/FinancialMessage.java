package com.buyorwait.model;

import java.time.Instant;
import java.util.Optional;

public record FinancialMessage(
        String messageId,
        String userId,
        Optional<String> requestId,
        Optional<String> relatedEventId,
        Instant sentAt,
        MessageSourceType sourceType,
        String messageText) {
}
