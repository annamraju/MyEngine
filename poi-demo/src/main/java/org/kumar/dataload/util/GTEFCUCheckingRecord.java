/**
 * 
 */
package org.kumar.dataload.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.kumar.excel.util.LedgerRecord;

/**
 * Record for CSV exports that look like:
 * "Order","Transaction Date","Description","Amount","Balance","Reference"
 *
 * Example rows:
 * "8","12/29/2025","GOLDMAN SACHS BA  - TRANSFER ","-$3,100.00","$42.52","2244"
 *
 * Works with MonthlyLoad:
 *  - getTransactionDate() -> used to compute DateInterval (26th->25th logic)
 *  - convertToLedgerRecord(accountName, order) -> used to add to ledger
 *  - readTransactionsFromCsv(Path) -> static reader returns sorted list old->new
 */
public class GTEFCUCheckingRecord implements LedgerInterface {

	    // Accepts both "12/08/2025" and "12/8/2025"
	    private static final DateTimeFormatter DATE_FMT = new DateTimeFormatterBuilder()
	            .appendValue(ChronoField.MONTH_OF_YEAR)  // M or MM
	            .appendLiteral('/')
	            .appendValue(ChronoField.DAY_OF_MONTH)   // d or dd
	            .appendLiteral('/')
	            .appendValue(ChronoField.YEAR, 4)        // yyyy
	            .toFormatter(Locale.US);

	    private int order;                 // "Order" column
	    private LocalDate transactionDate; // "Transaction Date"
	    private String description;        // "Description"
	    private double amount;             // "Amount" (signed)
	    private double balance;            // "Balance"
	    private String reference;          // "Reference" (optional-ish)

	    public GTEFCUCheckingRecord() {
	    }

	    // --- getters/setters ---
	    public int getOrder() {
	        return order;
	    }

	    public void setOrder(int order) {
	        this.order = order;
	    }

	    // MonthlyLoad expects this name (same pattern as your other record classes)
	    public LocalDate getTransactionDate() {
	        return transactionDate;
	    }

	    public void setTransactionDate(LocalDate transactionDate) {
	        this.transactionDate = transactionDate;
	    }

	    public String getDescription() {
	        return description;
	    }

	    public void setDescription(String description) {
	        this.description = description;
	    }

	    public double getAmount() {
	        return amount;
	    }

	    public void setAmount(double amount) {
	        this.amount = amount;
	    }

	    public double getBalance() {
	        return balance;
	    }

	    public void setBalance(double balance) {
	        this.balance = balance;
	    }

	    public String getReference() {
	        return reference;
	    }

	    public void setReference(String reference) {
	        this.reference = reference;
	    }

	    @Override
	    public String toString() {
	        return "GTEFCUCheckingRecord{" +
	                "order=" + order +
	                ", transactionDate=" + transactionDate +
	                ", description='" + description + '\'' +
	                ", amount=" + amount +
	                ", balance=" + balance +
	                ", reference='" + reference + '\'' +
	                '}';
	    }

	    /**
	     * LedgerInterface implementation (same pattern as ChaseCheckingRecord).
	     * We store the statement running balance into the LedgerRecord "balance" field.
	     *
	     * NOTE: LedgerRecord ctor signature assumed to match your project usage.
	     */
	    public LedgerRecord convertToLedgerRecord(String accountName, short orderInLedger) {
	        short year = (short) this.transactionDate.getYear();
	        String month = this.transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
	        Date transDate = Date.from(this.transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

	        // If you want reference embedded, you can append it to description:
	        // String desc = (reference == null || reference.isBlank()) ? description : (description + " [Ref " + reference + "]");
	        String desc = this.description;

	        return new LedgerRecord(
	                accountName,
	                orderInLedger,
	                year,
	                month,
	                transDate,
	                desc,
	                this.amount,
	                this.balance,
	                null, null, null, null, null
	        );
	    }

	    /**
	     * Static reader that returns transactions sorted old -> new (stable for same-day rows).
	     * This matches the expectation used by MonthlyLoad when applying order increments.
	     */
	    public static List<GTEFCUCheckingRecord> readTransactionsFromCsv(Path csvPath) throws IOException {

	        class RowWrap {
	            final int originalIndex;
	            final GTEFCUCheckingRecord rec;

	            RowWrap(int originalIndex, GTEFCUCheckingRecord rec) {
	                this.originalIndex = originalIndex;
	                this.rec = rec;
	            }
	        }

	        List<RowWrap> rows = new ArrayList<>();

	        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
	            String line;
	            boolean headerSkipped = false;
	            int idx = 0;

	            while ((line = br.readLine()) != null) {
	                if (!headerSkipped) {
	                    headerSkipped = true; // skip header
	                    continue;
	                }
	                if (line.trim().isEmpty()) continue;

	                List<String> cols = parseCsvLine(line);

	                // Expected columns:
	                // 0 Order, 1 Transaction Date, 2 Description, 3 Amount, 4 Balance, 5 Reference
	                if (cols.size() < 5) continue;

	                GTEFCUCheckingRecord r = new GTEFCUCheckingRecord();

	                r.setOrder(parseIntSafe(cols.get(0)));
	                r.setTransactionDate(parseDate(cols.get(1)));
	                r.setDescription(trimOrEmpty(cols.get(2)));
	                r.setAmount(parseMoney(cols.get(3)));
	                r.setBalance(parseMoney(cols.get(4)));

	                if (cols.size() >= 6) {
	                    String ref = trimOrEmpty(cols.get(5));
	                    r.setReference(ref.isEmpty() ? null : ref);
	                }

	                // Skip rows with missing dates (bad parse)
	                if (r.getTransactionDate() == null) continue;

	                rows.add(new RowWrap(idx++, r));
	            }
	        }

	        // old -> new, stable within same date
	        rows.sort(Comparator
	                .comparing((RowWrap w) -> w.rec.getTransactionDate())
	                .thenComparingInt(w -> w.originalIndex));

	        List<GTEFCUCheckingRecord> out = new ArrayList<>(rows.size());
	        for (RowWrap w : rows) out.add(w.rec);
	        return out;
	    }

	    // ---------------- helpers ----------------

	    /** Minimal CSV parser (supports quotes + commas inside quotes). */
	    private static List<String> parseCsvLine(String line) {
	        List<String> out = new ArrayList<>();
	        if (line == null) return out;

	        StringBuilder cur = new StringBuilder();
	        boolean inQuotes = false;

	        for (int i = 0; i < line.length(); i++) {
	            char c = line.charAt(i);

	            if (c == '"') {
	                // doubled quotes ("")
	                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
	                    cur.append('"');
	                    i++;
	                } else {
	                    inQuotes = !inQuotes;
	                }
	            } else if (c == ',' && !inQuotes) {
	                out.add(cur.toString());
	                cur.setLength(0);
	            } else {
	                cur.append(c);
	            }
	        }
	        out.add(cur.toString());
	        return out;
	    }

	    private static String trimOrEmpty(String s) {
	        if (s == null) return "";
	        String t = s.trim();
	        // strip surrounding quotes if any (defensive)
	        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
	            t = t.substring(1, t.length() - 1).trim();
	        }
	        return t;
	    }

	    private static int parseIntSafe(String s) {
	        String t = trimOrEmpty(s);
	        if (t.isEmpty()) return 0;
	        return Integer.parseInt(t);
	    }

	    private static LocalDate parseDate(String s) {
	        String t = trimOrEmpty(s);
	        if (t.isEmpty()) return null;

	        // Most files are M/d/yyyy; this handles both 12/8/2025 and 12/08/2025
	        return LocalDate.parse(t, DATE_FMT);
	    }

	    /**
	     * Parses values like:
	     *  "$2,865.00"
	     *  "-$3,100.00"
	     *  "($12.34)"   (defensive)
	     *  "" -> 0
	     */
	    private static double parseMoney(String s) {
	        String t = trimOrEmpty(s);
	        if (t.isEmpty()) return 0.0;

	        boolean negative = false;

	        // (123.45) style
	        if (t.startsWith("(") && t.endsWith(")")) {
	            negative = true;
	            t = t.substring(1, t.length() - 1).trim();
	        }

	        // "-$3,100.00" style
	        if (t.startsWith("-")) {
	            negative = true;
	            t = t.substring(1).trim();
	        }

	        t = t.replace("$", "").replace(",", "").trim();
	        if (t.isEmpty()) return 0.0;

	        double v = Double.parseDouble(t);
	        return negative ? -v : v;
	    }
	}
