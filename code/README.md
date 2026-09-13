# Buy or Wait? Java 21 solution

This Maven project targets Java 21. It loads the dataset, applies supported message evidence, forecasts cash flow,
validates payment candidates, and writes predictions and a sample evaluation report. Maven and a Java 21 JDK are required.

Run the complete pipeline from the repository root:

```bash
mvn -f code/pom.xml clean test
mvn -q -f code/pom.xml exec:java -Dexec.args="--run"
```

This writes exactly one row for each evaluation request to `dataset/output.csv` and evaluates all solved samples into
`code/evaluation/sample_predictions.csv`, `sample_evaluation.csv`, and `sample_evaluation.md`. The evaluation CSV
contains expected/actual values and match results for every field of every sample. `usage_report.md` records zero
model calls/tokens/cost for this deterministic run. Existing output and evaluation artifacts are replaced on each run.
Use `--run --dataset /path/to/dataset` for another dataset; reports are written to its parent directory's `code/evaluation/`.

Run from the repository root:

```bash
mvn -f code/pom.xml test
mvn -f code/pom.xml exec:java
```

The application finds `dataset/` by walking upward from the working directory. To use another dataset directory:

```bash
mvn -f code/pom.xml exec:java -Dexec.args="--dataset /path/to/dataset"
```

The loader uses UTF-8 and Apache Commons CSV. All monetary values use `BigDecimal`; a blank financial-event amount remains missing until a later evidence-extraction phase.

The current finance layer can also inspect a request's resolved baseline forecast without producing a recommendation:

```bash
mvn -f code/pom.xml exec:java -Dexec.args="--diagnose request_01"
mvn -f code/pom.xml exec:java -Dexec.args="--diagnose-details request_01"
```

The compact diagnostic reports only the 90-day cash-flow result, minimum balance, violation date, and unresolved evidence.
The detailed diagnostic prints the resolved/projected cash-flow timeline in home currency with running balances.

Message extraction is separate from deterministic event binding and validation. Supported English/Indonesian clauses
produce typed salary amount/date terms, employment-end evidence, rent increases, linked settlement/pending/failed/non-cash
states, and transaction clarifications. `MessageEvidenceParser` can be replaced without giving its implementation control
over payment decisions. Unsupported clauses are diagnostic notes; they do not execute instructions or invent cash.
Request-aware forecasts use unscoped user messages plus messages for that request, dated no later than the request's UTC
calendar date. Profile/date-only forecast overloads use unscoped messages. A statement about the next salary changes only
that occurrence; explicitly ongoing terms apply from their effective date. Later cycles otherwise retain the recurrence
estimator. Same-source precedence uses the dataset's structured `source_type`, since there is no separate sender ID.

The affordability pipeline calculates today's capacity and the earliest safe full-payment date before any optional
spending changes. Candidate payments keep the original 90-day horizon and the engine's existing same-day ordering
(debits, proposed payments, then credits). Every recommended candidate must complete by the deadline, stay inside that
horizon, and pass ForecastEngine. Supplied installment dates, frequencies, amounts and totals are preserved exactly.
Installment tenure is interpreted as the last due date being no later than first payment plus `max_installment_months`
calendar months; the specification does not give a more precise duration formula.

When no unchanged plan qualifies, the search enumerates up to three distinct eligible events, using permitted stops and
reductions to supplied `minimum_allowed_amount` floors. Protected categories are excluded, and missing reduction floors
are not invented. This searches for a valid plan rather than minimizing the magnitude of spending cuts. Unresolved
evidence produces zero verified capacity, `not_affordable` / `not_recommended`, and an explicit explanation. This fallback
is not proof of inability to pay; image extraction and unsupported message interpretation remain incomplete.

Compare variable recurring debit estimators across all rows in `sample_requests.csv`:

```bash
mvn -q -f code/pom.xml compile exec:java -Dexec.args="--compare-recurrence-amounts"
# Optional dataset location:
mvn -q -f code/pom.xml exec:java -Dexec.args="--compare-recurrence-amounts --dataset /path/to/dataset"
```

This prints a CSV comparison to stdout; it does not write `output.csv`. Each row contains the production baseline
minimum, minima using `current_max`, `historical_mean`, `historical_median`, `recent_3_mean`, `recent_5_mean`,
and `recent_3_max`, plus the three requested expected fields read directly from the solved sample.
All minima are balances in home currency before any proposed purchase or spending changes, not amounts safe to pay.
Rows marked `UNRESOLVED` show partial known-flow minima only; their evidence errors apply to every estimator.

Variable means differing home-currency amounts within the detected recurring sequence, independently of spending
flexibility. The analyzer exposes immutable chronological series amounts as diagnostic metadata; its production
estimator is unchanged. Only variable projected debits are replaced in diagnostic flow copies. Fixed recurring
amounts, credits, explicit scheduled payments, and pending reservations retain their production values.
Means use BigDecimal DECIMAL128 division (34 significant digits, HALF_EVEN); even medians use the exact mean
of the middle two values. Recent windows use the last up to 3 or 5 series observations. Historical currency
conversion uses the existing exact settlement-date converter. No payment plans or recommendations are evaluated.
