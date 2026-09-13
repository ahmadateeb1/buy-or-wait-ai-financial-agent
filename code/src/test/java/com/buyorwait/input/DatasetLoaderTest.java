package com.buyorwait.input;

import com.buyorwait.model.CurrencyCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetLoaderTest {
    @TempDir
    Path directory;

    @Test
    void parsesCsvAndKeepsBlankEventAmountsMissing() throws Exception {
        Dataset dataset = new DatasetLoader().load(DatasetFixture.create(directory));

        assertEquals(new BigDecimal("123.45"), dataset.profiles().getFirst().currentAvailableBalance());
        assertTrue(dataset.events().getFirst().amount().isEmpty());
        assertTrue(dataset.events().getFirst().settlementDate().isEmpty());
        assertEquals(CurrencyCode.INR, dataset.profiles().getFirst().homeCurrency());
    }

    @Test
    void createsExpectedIndexesForDatasetRelationships() throws Exception {
        Dataset dataset = new DatasetLoader().load(DatasetFixture.create(directory));
        DatasetIndex index = new DatasetIndex(dataset);

        assertTrue(index.profileByUserId("user_1").isPresent());
        assertTrue(index.eventById("event_1").isPresent());
        assertEquals(1, index.eventsByUserId("user_1").size());
        assertEquals(1, index.messagesByRequestId("request_1").size());
        assertEquals(1, index.messagesByRelatedEventId("event_1").size());
        assertEquals(1, index.imagesByRequestId("request_1").size());
        assertEquals(1, index.imagesByRelatedEventId("event_1").size());
        assertEquals(1, index.paymentOptionsByRequestId("request_1").size());
        assertTrue(index.exchangeRate(java.time.LocalDate.of(2026, 1, 1), CurrencyCode.USD, CurrencyCode.INR).isPresent());
    }
}
