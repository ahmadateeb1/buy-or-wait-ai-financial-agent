package com.buyorwait.input;

import com.buyorwait.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class DatasetValidator {
    public void validate(Dataset dataset) {
        List<String> errors = new ArrayList<>();
        Set<String> profileIds = uniqueIds(dataset.profiles(), FinancialProfile::userId, "financial profile", errors);
        Set<String> eventIds = uniqueIds(dataset.events(), FinancialEvent::eventId, "event", errors);
        Set<String> requestIds = uniqueIds(dataset.requests(), FinanceRequest::requestId, "request", errors);
        Set<String> sampleRequestIds = uniqueIds(dataset.sampleRequests().stream().map(SampleRequest::request).toList(),
                FinanceRequest::requestId, "sample request", errors);
        for (String id : sampleRequestIds) if (!requestIds.add(id)) errors.add("request ID is duplicated across evaluation and sample data: " + id);
        uniqueIds(dataset.paymentOptions(), PaymentOption::paymentOptionId, "payment option", errors);
        uniqueIds(dataset.messages(), FinancialMessage::messageId, "message", errors);
        uniqueIds(dataset.images(), ImageReference::imageId, "image", errors);
        uniqueIds(dataset.exchangeRates(), rate -> new ExchangeRateKey(rate.rateDate(), rate.fromCurrency(), rate.toCurrency()),
                "exchange-rate key", errors);

        Set<String> allRequestIds = new HashSet<>(requestIds);
        Set<String> imageEventIds = new HashSet<>();
        for (ImageReference image : dataset.images()) {
            required(image.userId(), "image " + image.imageId() + " user_id", errors);
            if (!profileIds.contains(image.userId())) errors.add("image " + image.imageId() + " has unknown user " + image.userId());
            image.requestId().ifPresent(id -> validateRequestReference("image " + image.imageId(), image.userId(), id, dataset, allRequestIds, errors));
            image.relatedEventId().ifPresent(id -> {
                imageEventIds.add(id);
                validateEventReference("image " + image.imageId(), image.userId(), id, dataset, eventIds, errors);
            });
        }

        for (FinancialEvent event : dataset.events()) {
            required(event.userId(), "event " + event.eventId() + " user_id", errors);
            required(event.currency(), "event " + event.eventId() + " currency", errors);
            if (!profileIds.contains(event.userId())) errors.add("event " + event.eventId() + " has unknown user " + event.userId());
            if (event.amount().isEmpty() && !imageEventIds.contains(event.eventId())) {
                errors.add("event " + event.eventId() + " has a missing amount but no related image reference");
            }
            event.linkedEventId().ifPresent(id -> validateEventReference("event " + event.eventId(), event.userId(), id, dataset, eventIds, errors));
        }

        for (FinanceRequest request : dataset.requests()) validateRequest(request, profileIds, errors);
        for (SampleRequest sample : dataset.sampleRequests()) validateRequest(sample.request(), profileIds, errors);
        for (PaymentOption option : dataset.paymentOptions()) {
            required(option.requestId(), "payment option " + option.paymentOptionId() + " request_id", errors);
            required(option.paymentAmount(), "payment option " + option.paymentOptionId() + " payment_amount", errors);
            required(option.financingFee(), "payment option " + option.paymentOptionId() + " financing_fee", errors);
            required(option.totalPayableAmount(), "payment option " + option.paymentOptionId() + " total_payable_amount", errors);
            if (!allRequestIds.contains(option.requestId())) errors.add("payment option " + option.paymentOptionId()
                    + " refers to unknown request " + option.requestId());
            if (option.paymentMethod() != PaymentMethod.FULL_PAYMENT && option.paymentMethod() != PaymentMethod.INSTALLMENTS) {
                errors.add("payment option " + option.paymentOptionId() + " has unsupported option method " + option.paymentMethod().csvValue());
            }
        }
        for (FinancialMessage message : dataset.messages()) {
            required(message.userId(), "message " + message.messageId() + " user_id", errors);
            if (!profileIds.contains(message.userId())) errors.add("message " + message.messageId() + " has unknown user " + message.userId());
            message.requestId().ifPresent(id -> validateRequestReference("message " + message.messageId(), message.userId(), id, dataset, allRequestIds, errors));
            message.relatedEventId().ifPresent(id -> validateEventReference("message " + message.messageId(), message.userId(), id, dataset, eventIds, errors));
        }
        for (FinancialProfile profile : dataset.profiles()) {
            required(profile.homeCurrency(), "profile " + profile.userId() + " home_currency", errors);
            required(profile.currentAvailableBalance(), "profile " + profile.userId() + " current_available_balance", errors);
            required(profile.minimumBalanceToKeep(), "profile " + profile.userId() + " minimum_balance_to_keep", errors);
        }
        for (ExchangeRate rate : dataset.exchangeRates()) {
            required(rate.fromCurrency(), "exchange rate from_currency", errors);
            required(rate.toCurrency(), "exchange rate to_currency", errors);
            required(rate.rate(), "exchange rate rate", errors);
        }
        if (!errors.isEmpty()) throw new DatasetValidationException(errors);
    }

    private static void validateRequest(FinanceRequest request, Set<String> profileIds, List<String> errors) {
        required(request.userId(), "request " + request.requestId() + " user_id", errors);
        required(request.requestedAmount(), "request " + request.requestId() + " requested_amount", errors);
        if (!profileIds.contains(request.userId())) errors.add("request " + request.requestId() + " has unknown user " + request.userId());
    }

    private static void validateRequestReference(String owner, String userId, String requestId, Dataset dataset,
            Set<String> requestIds, List<String> errors) {
        if (!requestIds.contains(requestId)) {
            errors.add(owner + " refers to unknown request " + requestId);
            return;
        }
        Optional<FinanceRequest> request = findRequest(dataset, requestId);
        request.ifPresent(found -> {
            if (!found.userId().equals(userId)) errors.add(owner + " user " + userId + " does not match request user " + found.userId());
        });
    }

    private static Optional<FinanceRequest> findRequest(Dataset dataset, String requestId) {
        return java.util.stream.Stream.concat(dataset.requests().stream(), dataset.sampleRequests().stream().map(SampleRequest::request))
                .filter(request -> request.requestId().equals(requestId)).findFirst();
    }

    private static void validateEventReference(String owner, String userId, String eventId, Dataset dataset,
            Set<String> eventIds, List<String> errors) {
        if (!eventIds.contains(eventId)) {
            errors.add(owner + " refers to unknown event " + eventId);
            return;
        }
        dataset.events().stream().filter(event -> event.eventId().equals(eventId)).findFirst().ifPresent(event -> {
            if (!event.userId().equals(userId)) errors.add(owner + " user " + userId + " does not match event user " + event.userId());
        });
    }

    private static <T, K> Set<K> uniqueIds(Collection<T> values, Function<T, K> key, String label, List<String> errors) {
        Set<K> ids = new HashSet<>();
        for (T value : values) if (!ids.add(key.apply(value))) errors.add("duplicate " + label + " ID: " + key.apply(value));
        return ids;
    }

    private static void required(Object value, String label, List<String> errors) {
        if (value == null || (value instanceof String text && text.isBlank())) errors.add(label + " is required");
    }
}
