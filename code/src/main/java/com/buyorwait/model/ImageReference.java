package com.buyorwait.model;

import java.util.Optional;

public record ImageReference(
        String imageId,
        String userId,
        Optional<String> requestId,
        Optional<String> relatedEventId) {
}
