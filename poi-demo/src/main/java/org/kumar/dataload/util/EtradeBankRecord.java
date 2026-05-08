package org.kumar.dataload.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import org.kumar.excel.util.LedgerRecord;

public class EtradeBankRecord implements LedgerInterface {

	    //private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy");
	    private static final DateTimeFormatter DATE_FLEX =
	            new DateTimeFormatterBuilder()
	                    .appendPattern("M/d/")
	                    .appendValueReduced(ChronoField.YEAR, 2, 4, 2000) // 25 -> 2025
	                    .toFormatter();

	    private LocalDate transactionDate;
	    private String transactionType;
	    private String description;
	    private String categories;
	    private double amount;
	    private double balance;

	    public EtradeBankRecord() {}

	    // ---- getters / setters ----
	    public LocalDate getTransactionDate() { return transactionDate; }
	    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

	    public String getTransactionType() { return transactionType; }
	    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

	    public String getDescription() { return description; }
	    public void setDescription(String description) { this.description = description; }

	    public String getCategories() { return categories; }
	    public void setCategories(String categories) { this.categories = categories; }

	    public double getAmount() { return amount; }
	    public void setAmount(double amount) { this.amount = amount; }

	    public double getBalance() { return balance; }
	    public void setBalance(double balance) { this.balance = balance; }

	    @Override
	    public String toString() {
	        return "EtradeBankRecord{" +
	                "transactionDate=" + transactionDate +
	                ", transactionType='" + transactionType + '\'' +
	                ", description='" + description + '\'' +
	                ", categories='" + categories + '\'' +
	                ", amount=" + amount +
	                ", balance=" + balance +
	                '}';
	    }

	    /**
	     * Works with MonthlyLoad generic: convert record -> LedgerRecord.
	     * Uses CSV "balance" field as LedgerRecord balance.
	     */
	    public LedgerRecord convertToLedgerRecord(String accountName, short order) {
	        short year = (short) transactionDate.getYear();
	        String month = transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
	        Date transDate = Date.from(transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

	        // Put a richer description if you want (optional)
	        String fullDesc = description;
	        //This is NOT needed
	        //if (transactionType != null && !transactionType.isBlank()) {
	        //    fullDesc = transactionType + " - " + description;
	        //}

	        return new LedgerRecord(
	                accountName,
	                order,
	                year,
	                month,
	                transDate,
	                fullDesc,
	                amount,
	                balance,
	                null, null, null, null, null
	        );
	    }

	    /**
	     * Reads CSV export that contains a header block then:
	     * TransactionDate,TransactionType,Description,Categories,Amount,Balance
	     *
	     * Returns transactions sorted old -> new (stable for same-day).
	     */
	    public static List<EtradeBankRecord> readTransactionsFromCsv(Path csvPath) throws IOException {

	        class RowWrap {
	            final int originalIndex;
	            final EtradeBankRecord rec;
	            RowWrap(int originalIndex, EtradeBankRecord rec) {
	                this.originalIndex = originalIndex;
	                this.rec = rec;
	            }
	        }

	        List<RowWrap> rows = new ArrayList<>();
	        boolean inTxnSection = false;
	        int idx = 0;

	        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
	            String line;
	            while ((line = br.readLine()) != null) {
	                if (line.trim().isEmpty()) continue;

	                // Find the real header row
	                if (!inTxnSection) {
	                    if (line.startsWith("TransactionDate,TransactionType,Description,Categories,Amount,Balance")) {
	                        inTxnSection = true;
	                    }
	                    continue;
	                }

	                // After header: ignore spacer rows like ",,,,,"
	                if (line.replace(",", "").trim().isEmpty()) {
	                    continue;
	                }

	                List<String> cols = parseCsvLine(line);
	                if (cols.size() < 6) continue;

	                String dateStr = safeGet(cols, 0).trim();
	                String type = safeGet(cols, 1).trim();
	                String desc = safeGet(cols, 2).trim();
	                String cats = safeGet(cols, 3).trim();
	                String amtStr = safeGet(cols, 4);
	                String balStr = safeGet(cols, 5);

	                EtradeBankRecord r = new EtradeBankRecord();
	                r.setTransactionDate(LocalDate.parse(dateStr, DATE_FLEX));
	                r.setTransactionType(type);
	                r.setDescription(desc);
	                r.setCategories(cats);
	                r.setAmount(parseMoney(amtStr));
	                r.setBalance(parseMoney(balStr));

	                rows.add(new RowWrap(idx++, r));
	            }
	        }

	        // old -> new, stable within same date
	        rows.sort(Comparator
	                .comparing((RowWrap w) -> w.rec.getTransactionDate())
	                .thenComparingInt(w -> w.originalIndex));

	        List<EtradeBankRecord> out = new ArrayList<>(rows.size());
	        for (RowWrap w : rows) out.add(w.rec);
	        return out;
	    }

	    // --- helpers ---

	    private static String safeGet(List<String> cols, int i) {
	        return (i >= 0 && i < cols.size() && cols.get(i) != null) ? cols.get(i) : "";
	    }

	    /** Supports commas inside quotes and "" escaping. */
	    private static List<String> parseCsvLine(String line) {
	        List<String> out = new ArrayList<>();
	        StringBuilder cur = new StringBuilder();
	        boolean inQuotes = false;

	        for (int i = 0; i < line.length(); i++) {
	            char c = line.charAt(i);

	            if (c == '"') {
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

	    /** Parses: 1,234.56  -12.00  $5.00  ""  -> double */
	    private static double parseMoney(String s) {
	        if (s == null) return 0.0;
	        String t = s.trim();
	        if (t.isEmpty()) return 0.0;

	        // strip surrounding quotes
	        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
	            t = t.substring(1, t.length() - 1).trim();
	        }

	        t = t.replace(",", "").replace("$", "").trim();
	        if (t.isEmpty()) return 0.0;

	        return Double.parseDouble(t);
	    }
	}
