package com.buyorwait.input;

import com.buyorwait.model.ExchangeRate;
import com.buyorwait.model.FinanceRequest;
import com.buyorwait.model.FinancialEvent;
import com.buyorwait.model.FinancialMessage;
import com.buyorwait.model.FinancialProfile;
import com.buyorwait.model.ImageReference;
import com.buyorwait.model.PaymentOption;
import com.buyorwait.model.SampleRequest;

import java.util.List;

public record Dataset(
        List<FinancialProfile> profiles,
        List<FinancialEvent> events,
        List<ExchangeRate> exchangeRates,
        List<FinanceRequest> requests,
        List<PaymentOption> paymentOptions,
        List<FinancialMessage> messages,
        List<ImageReference> images,
        List<SampleRequest> sampleRequests) {
    public Dataset {
        profiles = List.copyOf(profiles);
        events = List.copyOf(events);
        exchangeRates = List.copyOf(exchangeRates);
        requests = List.copyOf(requests);
        paymentOptions = List.copyOf(paymentOptions);
        messages = List.copyOf(messages);
        images = List.copyOf(images);
        sampleRequests = List.copyOf(sampleRequests);
    }
}
