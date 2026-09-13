# Sample evaluation

Evaluated 25 solved samples with the production pipeline. Expected values were read only for this report.

| Field | Matches | Total |
| --- | ---: | ---: |
| request_id | 25 | 25 |
| amount_safe_to_pay | 1 | 25 |
| affordability_status | 11 | 25 |
| recommended_payment_method | 11 | 25 |
| payment_plan | 10 | 25 |
| earliest_date_for_full_payment | 7 | 25 |
| spending_changes_needed | 21 | 25 |
| decision_explanation | 0 | 25 |

All six structured decision fields matched for 1/25 samples (excluding ID and explanation).

Amounts are compared as exact BigDecimal values, ignoring trailing zeros. Payment plans compare dates and exact amounts in order. Spending changes compare actions independent of order. Explanation matching is literal text equality, not a quality or groundedness score.

Full expected/actual comparisons for every field and request: [sample_evaluation.csv](sample_evaluation.csv). Predictions: [sample_predictions.csv](sample_predictions.csv).

## Amount error by currency

| Currency | Samples | Mean absolute error |
| --- | ---: | ---: |
| EUR | 8 | 220.64 |
| IDR | 5 | 1674877.00 |
| INR | 7 | 26019.02 |
| USD | 1 | 31.05 |
| ZAR | 4 | 14618.78 |

## Evidence limitations

- request_16: unresolved_amount:event_1442, unresolved_message_evidence
- request_20: unresolved_amount:event_1786

Unresolved forecasts use 0 / not_affordable / not_recommended with an explicit evidence explanation; this is a conservative output fallback, not proof of inability to pay. No image extraction or model calls were added. Unsupported message interpretations remain outside candidate validation.

## Fixed implementation interpretations

- The horizon is request_date through request_date + 89 days; it does not restart at a proposed payment date.
- Existing same-day ordering is unchanged: ordinary debits, proposed payments, then credits.
- Deadlines and the horizon are hard candidate limits. Earliest full-payment capacity is calculated independently of preferences and the deadline.
- Installment duration is checked by final due date against first payment date plus max_installment_months calendar months; the specification does not provide a more precise tenure formula.
- Spending-change search enumerates up to three distinct eligible events, using stop or the supplied minimum_allowed_amount for reductions. It does not optimize how small a reduction can be.
- Ranking uses the specification's order. All candidates already satisfy the deadline; ties after the supplied option ID retain deterministic generation order.
- Recurrence detection and its conservative variable-debit maximum were not tuned to the sample labels. Disagreements are retained for review.
