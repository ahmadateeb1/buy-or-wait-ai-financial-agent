package com.buyorwait.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatasetValidatorTest {
    @TempDir
    Path directory;

    @Test
    void acceptsAValidImageBackedMissingAmount() throws Exception {
        Dataset dataset = new DatasetLoader().load(DatasetFixture.create(directory));
        assertDoesNotThrow(() -> new DatasetValidator().validate(dataset));
    }

    @Test
    void reportsBrokenEventReference() throws Exception {
        Path fixture = DatasetFixture.create(directory);
        Files.writeString(fixture.resolve("images.csv"), """
                image_id,user_id,request_id,related_event_id
                image_1,user_1,request_1,missing_event
                """);
        Dataset dataset = new DatasetLoader().load(fixture);

        DatasetValidationException exception = assertThrows(DatasetValidationException.class,
                () -> new DatasetValidator().validate(dataset));
        assertTrue(exception.getMessage().contains("unknown event missing_event"));
    }

    @Test
    void reportsPaymentOptionReferencingUnknownRequest() throws Exception {
        Path fixture = DatasetFixture.create(directory);
        Files.writeString(fixture.resolve("request_payment_options.csv"), """
                payment_option_id,request_id,payment_method,payment_amount,number_of_payments,first_payment_date,payment_frequency_days,financing_fee,total_payable_amount
                option_1,missing_request,full_payment,100,1,2026-01-01,,0,100
                """);
        Dataset dataset = new DatasetLoader().load(fixture);

        DatasetValidationException exception = assertThrows(DatasetValidationException.class,
                () -> new DatasetValidator().validate(dataset));
        assertTrue(exception.getMessage().contains("unknown request missing_request"));
    }
}
