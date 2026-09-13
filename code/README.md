# Buy or Wait? Java foundation

This Maven project targets Java 21 and currently implements only dataset loading, indexing, and integrity validation. It does not perform financial forecasting or produce `output.csv` yet.

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
