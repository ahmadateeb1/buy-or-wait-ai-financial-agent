package com.buyorwait.input;

import com.buyorwait.model.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class DatasetIndex {
    private final Map<String, FinancialProfile> profilesByUserId;
    private final Map<String, FinancialEvent> eventsById;
    private final Map<String, FinanceRequest> requestsById;
    private final Map<String, List<FinancialEvent>> eventsByUserId;
    private final Map<String, List<FinancialMessage>> messagesByUserId;
    private final Map<String, List<FinancialMessage>> messagesByRequestId;
    private final Map<String, List<FinancialMessage>> messagesByRelatedEventId;
    private final Map<String, List<ImageReference>> imagesByRequestId;
    private final Map<String, List<ImageReference>> imagesByRelatedEventId;
    private final Map<String, List<PaymentOption>> paymentOptionsByRequestId;
    private final Map<ExchangeRateKey, ExchangeRate> exchangeRatesByKey;

    public DatasetIndex(Dataset dataset) {
        profilesByUserId = uniqueMap(dataset.profiles(), FinancialProfile::userId);
        eventsById = uniqueMap(dataset.events(), FinancialEvent::eventId);
        List<FinanceRequest> allRequests = new ArrayList<>(dataset.requests());
        dataset.sampleRequests().forEach(sample -> allRequests.add(sample.request()));
        requestsById = uniqueMap(allRequests, FinanceRequest::requestId);
        eventsByUserId = groupBy(dataset.events(), FinancialEvent::userId);
        messagesByUserId = groupBy(dataset.messages(), FinancialMessage::userId);
        messagesByRequestId = groupOptional(dataset.messages(), FinancialMessage::requestId);
        messagesByRelatedEventId = groupOptional(dataset.messages(), FinancialMessage::relatedEventId);
        imagesByRequestId = groupOptional(dataset.images(), ImageReference::requestId);
        imagesByRelatedEventId = groupOptional(dataset.images(), ImageReference::relatedEventId);
        paymentOptionsByRequestId = groupBy(dataset.paymentOptions(), PaymentOption::requestId);
        exchangeRatesByKey = uniqueMap(dataset.exchangeRates(), rate -> new ExchangeRateKey(
                rate.rateDate(), rate.fromCurrency(), rate.toCurrency()));
    }

    public Optional<FinancialProfile> profileByUserId(String userId) { return Optional.ofNullable(profilesByUserId.get(userId)); }
    public Optional<FinancialEvent> eventById(String eventId) { return Optional.ofNullable(eventsById.get(eventId)); }
    public Optional<FinanceRequest> requestById(String requestId) { return Optional.ofNullable(requestsById.get(requestId)); }
    public List<FinancialEvent> eventsByUserId(String userId) { return eventsByUserId.getOrDefault(userId, List.of()); }
    public List<FinancialMessage> messagesByUserId(String userId) { return messagesByUserId.getOrDefault(userId, List.of()); }
    public List<FinancialMessage> messagesByRequestId(String requestId) { return messagesByRequestId.getOrDefault(requestId, List.of()); }
    public List<FinancialMessage> messagesByRelatedEventId(String eventId) { return messagesByRelatedEventId.getOrDefault(eventId, List.of()); }
    public List<ImageReference> imagesByRequestId(String requestId) { return imagesByRequestId.getOrDefault(requestId, List.of()); }
    public List<ImageReference> imagesByRelatedEventId(String eventId) { return imagesByRelatedEventId.getOrDefault(eventId, List.of()); }
    public List<PaymentOption> paymentOptionsByRequestId(String requestId) { return paymentOptionsByRequestId.getOrDefault(requestId, List.of()); }
    public Optional<ExchangeRate> exchangeRate(LocalDate date, CurrencyCode from, CurrencyCode to) {
        return Optional.ofNullable(exchangeRatesByKey.get(new ExchangeRateKey(date, from, to)));
    }

    private static <T, K> Map<K, T> uniqueMap(List<T> values, Function<T, K> key) {
        Map<K, T> result = new HashMap<>();
        for (T value : values) result.putIfAbsent(key.apply(value), value);
        return Map.copyOf(result);
    }

    private static <T, K> Map<K, List<T>> groupBy(List<T> values, Function<T, K> key) {
        Map<K, List<T>> result = new HashMap<>();
        for (T value : values) result.computeIfAbsent(key.apply(value), ignored -> new ArrayList<>()).add(value);
        return immutableLists(result);
    }

    private static <T, K> Map<K, List<T>> groupOptional(List<T> values, Function<T, Optional<K>> key) {
        Map<K, List<T>> result = new HashMap<>();
        for (T value : values) key.apply(value).ifPresent(actual -> result.computeIfAbsent(actual, ignored -> new ArrayList<>()).add(value));
        return immutableLists(result);
    }

    private static <K, T> Map<K, List<T>> immutableLists(Map<K, List<T>> values) {
        Map<K, List<T>> result = new HashMap<>();
        values.forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }
}
