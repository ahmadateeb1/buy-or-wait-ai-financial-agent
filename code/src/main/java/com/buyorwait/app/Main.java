package com.buyorwait.app;

import com.buyorwait.diagnostic.RecurrenceAmountComparison;
import com.buyorwait.input.Dataset;
import com.buyorwait.input.DatasetIndex;
import com.buyorwait.input.DatasetLoader;
import com.buyorwait.input.DatasetValidator;
import com.buyorwait.finance.CashFlowForecastService;
import com.buyorwait.finance.CashFlowKind;
import com.buyorwait.finance.ForecastDiagnostic;
import com.buyorwait.finance.ForecastResult;
import com.buyorwait.finance.ForecastTimelineEntry;
import com.buyorwait.finance.ResolvedCashFlow;
import com.buyorwait.model.FinanceRequest;
import com.buyorwait.model.FinancialEvent;
import com.buyorwait.model.FinancialProfile;
import com.buyorwait.model.EventStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Path datasetDirectory = resolveDatasetDirectory(args);
        Dataset dataset = new DatasetLoader().load(datasetDirectory);
        new DatasetValidator().validate(dataset);
        DatasetIndex index = new DatasetIndex(dataset);
        if (args.length >= 1 && "--compare-recurrence-amounts".equals(args[0])) {
            new RecurrenceAmountComparison(index).printSamples(dataset, System.out);
            return;
        }
        if (args.length == 2 && "--diagnose".equals(args[0])) {
            printDiagnostic(index, args[1]);
            return;
        }
        if (args.length == 2 && "--diagnose-details".equals(args[0])) {
            printDetailedDiagnostic(index, args[1]);
            return;
        }
        System.out.printf("Dataset integrity verified: profiles=%d, events=%d, exchangeRates=%d, requests=%d, "
                        + "paymentOptions=%d, messages=%d, images=%d, sampleRequests=%d%n",
                dataset.profiles().size(), dataset.events().size(), dataset.exchangeRates().size(), dataset.requests().size(),
                dataset.paymentOptions().size(), dataset.messages().size(), dataset.images().size(), dataset.sampleRequests().size());
    }

    private static Path resolveDatasetDirectory(String[] args) {
        if (args.length == 1 && "--compare-recurrence-amounts".equals(args[0])) return findDefaultDataset();
        if (args.length == 3 && "--compare-recurrence-amounts".equals(args[0]) && "--dataset".equals(args[1])) {
            return checkedDataset(Path.of(args[2]));
        }
        if (args.length == 2 && "--dataset".equals(args[0])) return checkedDataset(Path.of(args[1]));
        if (args.length == 2 && ("--diagnose".equals(args[0]) || "--diagnose-details".equals(args[0]))) return findDefaultDataset();
        if (args.length != 0) throw new IllegalArgumentException(
                "Usage: Main [--dataset <dataset-directory> | --diagnose <request-id> | --diagnose-details <request-id>"
                        + " | --compare-recurrence-amounts [--dataset <dataset-directory>]]");
        return findDefaultDataset();
    }

    private static Path findDefaultDataset() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("dataset");
            if (Files.isDirectory(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalArgumentException("Cannot find dataset directory; pass --dataset <dataset-directory>");
    }

    private static void printDiagnostic(DatasetIndex index, String requestId) {
        FinanceRequest request = index.requestById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + requestId));
        var profile = index.profileByUserId(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Missing profile for request: " + requestId));
        ForecastResult result = new CashFlowForecastService(index).diagnose(request, List.of(), List.of()).result();
        System.out.printf("Forecast diagnostic: request=%s, status=%s, flows=%d, starting=%s, ending=%s, minimum=%s on %s, violation=%s, unresolved=%d%n",
                requestId, result.status(), result.timeline().size(), result.startingBalance(), result.endingBalance(),
                result.minimumProjectedBalance(), result.minimumBalanceDate(), result.firstViolationDate().orElse(null),
                result.unresolvedEvidence().size());
    }

    private static void printDetailedDiagnostic(DatasetIndex index, String requestId) {
        FinanceRequest request = index.requestById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown request: " + requestId));
        FinancialProfile profile = index.profileByUserId(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Missing profile for request: " + requestId));
        ForecastDiagnostic diagnostic = new CashFlowForecastService(index).diagnose(request, List.of(), List.of());
        ForecastResult result = diagnostic.result();
        Map<String, ForecastTimelineEntry> timelineById = new HashMap<>();
        for (ForecastTimelineEntry entry : result.timeline()) {
            timelineById.put(entry.entryId(), entry);
        }

        System.out.printf("Forecast diagnostic details: request=%s, user=%s, requestDate=%s, homeCurrency=%s%n",
                request.requestId(), request.userId(), request.requestDate(), profile.homeCurrency());
        System.out.printf("startingBalance=%s, minimum_balance_to_keep=%s, finalBalance=%s, firstViolationDate=%s%n",
                result.startingBalance(), profile.minimumBalanceToKeep(), result.endingBalance(),
                result.firstViolationDate().map(Object::toString).orElse("none"));
        for (var evidence : diagnostic.messageOverrides()) {
            var amendment = evidence.override();
            System.out.printf("message=%s, target=%s, amount=%s, currency=%s, settlementDate=%s, status=%s%n",
                    evidence.messageId(), amendment.eventId(), amendment.amount().map(Object::toString).orElse("unchanged"),
                    amendment.currency().map(Object::toString).orElse("unchanged"),
                    amendment.settlementDate().map(Object::toString).orElse("unchanged"),
                    amendment.status().map(Object::toString).orElse("unchanged"));
        }
        diagnostic.evidenceNotes().forEach(note -> System.out.println("evidence: " + note));
        result.unresolvedEvidence().forEach(issue -> System.out.println("unresolved: " + issue.code() + ": " + issue.message()));
        System.out.println("date,direction,amount_home_currency,balance_after,event_category,event_type,source_event_id,flow_kind");
        for (ResolvedCashFlow flow : diagnostic.cashFlows()) {
            ForecastTimelineEntry entry = timelineById.get(flow.flowId());
            if (entry == null) continue;
            Optional<FinancialEvent> event = flow.originEventId().flatMap(index::eventById);
            System.out.printf("%s,%s,%s,%s,%s,%s,%s,%s%n",
                    flow.date(),
                    flow.direction(),
                    flow.amount(),
                    entry.balanceAfter(),
                    event.map(FinancialEvent::category).orElse(flow.category()),
                    event.map(value -> value.eventType().csvValue()).orElse("none"),
                    flow.originEventId().orElse("none"),
                    classifyFlow(flow, event));
        }
    }

    private static String classifyFlow(ResolvedCashFlow flow, Optional<FinancialEvent> event) {
        if (flow.kind() == CashFlowKind.MESSAGE_EVIDENCE) return "message evidence";
        if (flow.kind() == CashFlowKind.PENDING_DEBIT_RESERVATION) return "pending reservation";
        if (flow.kind() == CashFlowKind.RECURRENCE) return "recurring projection";
        if (event.map(FinancialEvent::status).filter(EventStatus.SCHEDULED::equals).isPresent()) return "scheduled";
        return "explicit";
    }

    private static Path checkedDataset(Path directory) {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) throw new IllegalArgumentException("Dataset directory does not exist: " + normalized);
        return normalized;
    }
}
