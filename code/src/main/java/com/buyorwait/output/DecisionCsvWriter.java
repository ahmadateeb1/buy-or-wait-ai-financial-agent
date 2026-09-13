package com.buyorwait.output;

import com.buyorwait.decision.AffordabilityDecision;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class DecisionCsvWriter {
    public static final List<String> COLUMNS = List.of("request_id", "amount_safe_to_pay", "affordability_status",
            "recommended_payment_method", "payment_plan", "earliest_date_for_full_payment", "spending_changes_needed", "decision_explanation");

    public void write(Path output, List<AffordabilityDecision> decisions) throws IOException {
        Files.createDirectories(output.toAbsolutePath().getParent());
        if (decisions.stream().map(AffordabilityDecision::requestId).distinct().count() != decisions.size()) {
            throw new IllegalArgumentException("Duplicate output request IDs");
        }
        Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".decisions-", ".csv");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8);
                    var printer = new CSVPrinter(writer, CSVFormat.RFC4180.builder().setRecordSeparator("\n").setHeader(COLUMNS.toArray(String[]::new)).build())) {
                for (AffordabilityDecision decision : decisions) printer.printRecord(decision.columns());
            }
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
