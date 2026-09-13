package com.buyorwait.evidence;

import com.buyorwait.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;
import static com.buyorwait.evidence.MessageFact.*;

/** Allowlisted factual clauses observed in messages.csv, independent of IDs and company names. */
public final class DeterministicMessageEvidenceParser implements MessageEvidenceParser {
    private static final String MONEY = "([A-Z]{3}) ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?)(?=\\.(?:\\s|$)|[; ]|$)";
    private static final String DATE = "(\\d{4}-\\d{2}-\\d{2})";
    private static final String BOUNDARY = "(?:^|[.!?]\\s+)";
    private static final Pattern SALARY = Pattern.compile(BOUNDARY + "(?:"
            + "Your next salary is reduced to |Your temporary monthly pay is |Your regular salary for the next payroll is |"
            + "Your confirmed base salary is |Your monthly salary has increased to |The remaining confirmed monthly salary is |"
            + "Your first salary will be |Your first salary from the new employer is |Your (?:first )?salary of |Regular salary of |"
            + "Gaji bulanan Anda naik menjadi |Gaji bulanan sementara Anda adalah |Gaji rutin Anda untuk penggajian berikutnya adalah |"
            + "Gaji pokok yang dikonfirmasi adalah |Sisa gaji bulanan yang dikonfirmasi adalah |Gaji pertama dari perusahaan baru adalah |"
            + "Gaji pertama Anda sebesar |Gaji sebesar )" + MONEY, Pattern.CASE_INSENSITIVE);
    private static final Pattern SALARY_DATE = Pattern.compile("(?:The change applies from |Perubahan ini berlaku mulai |"
            + "resumes on |The confirmed credit date is |It is confirmed for |is confirmed for |is scheduled for |"
            + "Pembayaran sudah dikonfirmasi untuk |dikonfirmasi untuk |dijadwalkan pada |Tanggal kredit yang dikonfirmasi adalah )" + DATE,
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DELAY = Pattern.compile(BOUNDARY + "(?:Your confirmed salary is now expected on |"
            + "Gaji yang sudah dikonfirmasi kini diperkirakan masuk pada )" + DATE, Pattern.CASE_INSENSITIVE);
    private static final Pattern RENT = Pattern.compile(BOUNDARY + "(?:The renewed lease increases monthly rent by |"
            + "Perpanjangan sewa menaikkan biaya sewa bulanan sebesar )([0-9]+(?:\\.[0-9]+)?)%", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID_DATE = Pattern.compile("(?:payment was received on |was paid in [A-Z]{3} on |"
            + "was charged for [^.]*? on )(\\d{1,2} [A-Za-z]+ \\d{4})", Pattern.CASE_INSENSITIVE);

    @Override
    public List<MessageFact> parse(FinancialMessage message) {
        String text = message.messageText();
        List<MessageFact> facts = new ArrayList<>();
        if (message.sourceType() == MessageSourceType.EMPLOYER) {
            Matcher salary = SALARY.matcher(text);
            while (salary.find()) {
                Matcher date = SALARY_DATE.matcher(text);
                Optional<LocalDate> effective = date.find() ? Optional.of(LocalDate.parse(date.group(1))) : Optional.empty();
                String clause = salary.group().toLowerCase(Locale.ROOT);
                boolean ongoing = clause.contains("monthly salary has increased") || clause.contains("gaji bulanan anda naik")
                        || clause.contains("remaining confirmed monthly") || clause.contains("sisa gaji bulanan")
                        || clause.contains("regular salary of") || clause.contains("confirmed base salary")
                        || clause.contains("gaji pokok yang dikonfirmasi");
                facts.add(new SalaryAmount(new BigDecimal(salary.group(2).replace(",", "")),
                        CurrencyCode.valueOf(salary.group(1).toUpperCase(Locale.ROOT)), effective,
                        ongoing ? Scope.FROM_EFFECTIVE_DATE : Scope.NEXT_OCCURRENCE));
            }
            Matcher delay = DELAY.matcher(text);
            if (delay.find()) facts.add(new SalaryDate(LocalDate.parse(delay.group(1))));
            if (contains(text, "The current seasonal contract has ended.", "Your employment has ended.",
                    "Kontrak musiman saat ini telah berakhir.", "Hubungan kerja Anda telah berakhir.")) facts.add(new SalaryStopped());
            clarify(facts, text, ClarificationKind.REGULAR_SALARY_CONFIRMED, "Gaji rutin untuk penggajian berikutnya sudah dikonfirmasi.");
            clarify(facts, text, ClarificationKind.ONE_OFF_ARREARS, "one-time arrears adjustment", "penyesuaian tunggakan satu kali");
            clarify(facts, text, ClarificationKind.ONE_OFF_REIMBURSEMENT, "not your regular salary", "bukan gaji rutin Anda");
            clarify(facts, text, ClarificationKind.UNCONFIRMED_INCOME, "final amount and payment date have not been approved",
                    "Jumlah akhir dan tanggal pembayaran belum disetujui", "commission shown for open deals is still pending",
                    "Komisi dari transaksi yang masih berjalan belum disetujui");
        }
        if (message.sourceType() == MessageSourceType.SERVICE_PROVIDER) {
            Matcher rent = RENT.matcher(text);
            if (rent.find()) facts.add(new RentIncrease(new BigDecimal(rent.group(1))));
            clarify(facts, text, ClarificationKind.PENDING_PAYOUT, "payout is still pending", "masih tertunda");
            clarify(facts, text, ClarificationKind.APPROVED_INVOICE_AWAITING_SETTLEMENT,
                    "The client approved an invoice payment", "Klien menyetujui pembayaran faktur");
        }
        if (message.relatedEventId().isPresent()) {
            if (contains(text, "refund has been initiated but has not reached", "Pengembalian dana sudah diproses, tetapi belum masuk")) {
                facts.add(new EventState(EventStatus.PENDING, Optional.empty()));
            }
            if (contains(text, "The previous debit attempt failed.")) facts.add(new EventState(EventStatus.FAILED, Optional.empty()));
            if (contains(text, "prize proceeds have reached your account", "proceeds from your investment sale have settled in the cash account",
                    "Hasil penjualan investasi Anda sudah masuk ke rekening tunai")) facts.add(new EventState(EventStatus.SETTLED, Optional.empty()));
            if (contains(text, "No units have been sold and no cash proceeds have been generated", "holding has not been sold and there has been no cash transaction",
                    "Investasi tersebut belum dijual dan tidak ada transaksi tunai")) facts.add(new EventState(EventStatus.UNREALIZED, Optional.empty()));
            Matcher paid = PAID_DATE.matcher(text);
            if (paid.find()) facts.add(new EventState(EventStatus.SETTLED,
                    Optional.of(LocalDate.parse(paid.group(1), DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH)))));
        }
        clarify(facts, text, ClarificationKind.OWN_ACCOUNT_TRANSFER, "transfer between your two accounts", "transfer antara dua rekening Anda");
        clarify(facts, text, ClarificationKind.UNPOSTED_REVERSAL, "A reversal has not been posted", "Dana pembalikannya belum tercatat");
        clarify(facts, text, ClarificationKind.SEPARATE_CARD_MINIMUMS, "minimum payments due on two separate card accounts");
        clarify(facts, text, ClarificationKind.PENDING_PAYOUT, "payment has not been credited to your account yet", "Pembayaran tersebut belum masuk ke rekening Anda");
        clarify(facts, text, ClarificationKind.FOREIGN_SETTLEMENT_RATE, "foreign-currency refund is still processing",
                "bill was charged in a foreign currency", "Tagihan dikenakan dalam mata uang asing");
        return List.copyOf(facts);
    }

    private static boolean contains(String text, String... fragments) {
        String lower = text.toLowerCase(Locale.ROOT);
        return Arrays.stream(fragments).anyMatch(fragment -> lower.contains(fragment.toLowerCase(Locale.ROOT)));
    }

    private static void clarify(List<MessageFact> facts, String text, ClarificationKind kind, String... fragments) {
        if (contains(text, fragments)) facts.add(new Clarification(kind));
    }
}
