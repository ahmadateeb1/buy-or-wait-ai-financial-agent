package com.buyorwait.input;

import com.buyorwait.model.*;
import com.buyorwait.support.DateUtil;
import com.buyorwait.support.MoneyUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DatasetLoader {
    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

    public Dataset load(Path datasetDirectory) {
        return new Dataset(
                parse(datasetDirectory, "financial_profiles.csv", this::profile),
                parse(datasetDirectory, "financial_events.csv", this::event),
                parse(datasetDirectory, "exchange_rates.csv", this::exchangeRate),
                parse(datasetDirectory, "requests.csv", this::request),
                parse(datasetDirectory, "request_payment_options.csv", this::paymentOption),
                parse(datasetDirectory, "messages.csv", this::message),
                parse(datasetDirectory, "images.csv", this::image),
                parse(datasetDirectory, "sample_requests.csv", this::sampleRequest));
    }

    private FinancialProfile profile(CSVRecord record, String file) {
        return new FinancialProfile(
                requiredText(record, file, "user_id"),
                enumValue(CurrencyCode.class, record, file, "home_currency"),
                MoneyUtil.required(value(record, file, "current_available_balance"), file, row(record), "current_available_balance"),
                MoneyUtil.required(value(record, file, "minimum_balance_to_keep"), file, row(record), "minimum_balance_to_keep"),
                pipeSet(value(record, file, "financial_priorities")),
                pipeSet(value(record, file, "expense_categories_to_protect")),
                pipeSet(value(record, file, "expense_categories_user_is_willing_to_reduce")),
                pipeSet(value(record, file, "expense_categories_user_is_willing_to_stop")),
                paymentMethodSet(record, file, "payment_methods_user_will_consider"),
                optionalPositiveInt(record, file, "max_installment_months"));
    }

    private FinancialEvent event(CSVRecord record, String file) {
        return new FinancialEvent(
                requiredText(record, file, "event_id"), requiredText(record, file, "user_id"),
                enumValue(EventType.class, record, file, "event_type"), requiredText(record, file, "description"),
                requiredText(record, file, "category"), enumValue(EventDirection.class, record, file, "direction"),
                MoneyUtil.optional(value(record, file, "amount"), file, row(record), "amount"),
                enumValue(CurrencyCode.class, record, file, "currency"),
                DateUtil.requiredDate(value(record, file, "event_date"), file, row(record), "event_date"),
                DateUtil.optionalDate(value(record, file, "settlement_date"), file, row(record), "settlement_date"),
                enumValue(EventStatus.class, record, file, "status"), optionalText(record, file, "linked_event_id"),
                enumValue(Flexibility.class, record, file, "flexibility"),
                MoneyUtil.optional(value(record, file, "minimum_allowed_amount"), file, row(record), "minimum_allowed_amount"));
    }

    private ExchangeRate exchangeRate(CSVRecord record, String file) {
        return new ExchangeRate(
                DateUtil.requiredDate(value(record, file, "rate_date"), file, row(record), "rate_date"),
                enumValue(CurrencyCode.class, record, file, "from_currency"),
                enumValue(CurrencyCode.class, record, file, "to_currency"),
                MoneyUtil.required(value(record, file, "rate"), file, row(record), "rate"));
    }

    private FinanceRequest request(CSVRecord record, String file) {
        return new FinanceRequest(
                requiredText(record, file, "request_id"), requiredText(record, file, "user_id"),
                DateUtil.requiredDate(value(record, file, "request_date"), file, row(record), "request_date"),
                enumValue(RequestType.class, record, file, "request_type"),
                MoneyUtil.required(value(record, file, "requested_amount"), file, row(record), "requested_amount"),
                DateUtil.requiredDate(value(record, file, "desired_completion_date"), file, row(record), "desired_completion_date"),
                strictBoolean(record, file, "allows_partial_payment"), requiredText(record, file, "request_text"));
    }

    private PaymentOption paymentOption(CSVRecord record, String file) {
        return new PaymentOption(
                requiredText(record, file, "payment_option_id"), requiredText(record, file, "request_id"),
                enumValue(PaymentMethod.class, record, file, "payment_method"),
                MoneyUtil.required(value(record, file, "payment_amount"), file, row(record), "payment_amount"),
                requiredPositiveInt(record, file, "number_of_payments"),
                DateUtil.requiredDate(value(record, file, "first_payment_date"), file, row(record), "first_payment_date"),
                optionalPositiveInt(record, file, "payment_frequency_days"),
                MoneyUtil.required(value(record, file, "financing_fee"), file, row(record), "financing_fee"),
                MoneyUtil.required(value(record, file, "total_payable_amount"), file, row(record), "total_payable_amount"));
    }

    private FinancialMessage message(CSVRecord record, String file) {
        return new FinancialMessage(
                requiredText(record, file, "message_id"), requiredText(record, file, "user_id"),
                optionalText(record, file, "request_id"), optionalText(record, file, "related_event_id"),
                DateUtil.requiredInstant(value(record, file, "sent_at"), file, row(record), "sent_at"),
                enumValue(MessageSourceType.class, record, file, "source_type"), requiredText(record, file, "message_text"));
    }

    private ImageReference image(CSVRecord record, String file) {
        return new ImageReference(requiredText(record, file, "image_id"), requiredText(record, file, "user_id"),
                optionalText(record, file, "request_id"), optionalText(record, file, "related_event_id"));
    }

    private SampleRequest sampleRequest(CSVRecord record, String file) {
        return new SampleRequest(request(record, file), new SampleOutput(
                value(record, file, "amount_safe_to_pay"), value(record, file, "affordability_status"),
                value(record, file, "recommended_payment_method"), value(record, file, "payment_plan"),
                value(record, file, "earliest_date_for_full_payment"), value(record, file, "spending_changes_needed"),
                value(record, file, "decision_explanation")));
    }

    private <T> List<T> parse(Path directory, String filename, RowMapper<T> mapper) {
        Path path = directory.resolve(filename);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Required dataset file is missing: " + path);
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8); CSVParser parser = FORMAT.parse(reader)) {
            List<T> results = new ArrayList<>();
            for (CSVRecord record : parser) {
                results.add(mapper.map(record, filename));
            }
            return List.copyOf(results);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read " + path, exception);
        }
    }

    private static String value(CSVRecord record, String file, String column) {
        if (!record.isMapped(column)) {
            throw new IllegalArgumentException("%s is missing required header %s".formatted(file, column));
        }
        return record.get(column);
    }

    private static String requiredText(CSVRecord record, String file, String column) {
        String result = value(record, file, column);
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("%s row %d column %s is required".formatted(file, row(record), column));
        }
        return result;
    }

    private static Optional<String> optionalText(CSVRecord record, String file, String column) {
        String result = value(record, file, column);
        return result == null || result.isBlank() ? Optional.empty() : Optional.of(result);
    }

    private static long row(CSVRecord record) { return record.getRecordNumber() + 1; }

    private static <E extends Enum<E> & CsvValue> E enumValue(Class<E> type, CSVRecord record, String file, String column) {
        return EnumParser.parse(type, requiredText(record, file, column), file, row(record), column);
    }

    private static Set<String> pipeSet(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim).filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<PaymentMethod> paymentMethodSet(CSVRecord record, String file, String column) {
        String raw = value(record, file, column);
        if (raw == null || raw.isBlank()) return Set.of();
        Set<PaymentMethod> methods = new LinkedHashSet<>();
        for (String part : raw.split("\\|")) {
            methods.add(EnumParser.parse(PaymentMethod.class, part.trim(), file, row(record), column));
        }
        return Set.copyOf(methods);
    }

    private static boolean strictBoolean(CSVRecord record, String file, String column) {
        String raw = requiredText(record, file, column);
        if ("true".equals(raw)) return true;
        if ("false".equals(raw)) return false;
        throw new IllegalArgumentException("%s row %d column %s must be true or false, found '%s'"
                .formatted(file, row(record), column, raw));
    }

    private static int requiredPositiveInt(CSVRecord record, String file, String column) {
        Optional<Integer> parsed = optionalPositiveInt(record, file, column);
        return parsed.orElseThrow(() -> new IllegalArgumentException("%s row %d column %s is required"
                .formatted(file, row(record), column)));
    }

    private static Optional<Integer> optionalPositiveInt(CSVRecord record, String file, String column) {
        String raw = value(record, file, column);
        if (raw == null || raw.isBlank()) return Optional.empty();
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) throw new NumberFormatException("not positive");
            return Optional.of(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("%s row %d column %s must be a positive integer, found '%s'"
                    .formatted(file, row(record), column, raw), exception);
        }
    }

    @FunctionalInterface
    private interface RowMapper<T> {
        T map(CSVRecord record, String file);
    }
}
