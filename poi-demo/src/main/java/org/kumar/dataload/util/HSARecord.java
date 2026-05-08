package org.kumar.dataload.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.time.format.DateTimeFormatter;

import org.kumar.excel.util.LedgerRecord;

public class HSARecord implements LedgerInterface {

    private LocalDate transactionDate;
    private String description;
    private double amount;
    private double balance; // not provided by CSV; kept for interface symmetry / future use

    public HSARecord() {}

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

    @Override
    public String toString() {
        return "HSARecord{" +
                "transactionDate=" + transactionDate +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", balance=" + balance +
                '}';
    }

    /**
     * LedgerInterface implementation
     */
    @Override
    public LedgerRecord convertToLedgerRecord(String accountName, short order) {
        short year = (short) this.transactionDate.getYear();
        String month = this.transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        Date transDate = Date.from(this.transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        // Matches BOACheckingRecord pattern: amount populated, other numeric column left 0.0, rest null.
        return new LedgerRecord(
                accountName,
                order,
                year,
                month,
                transDate,
                this.description,
                this.amount,
                0.0,
                null, null, null, null, null
        );
    }

    /**
     * Reader for HSA Transactions CSV
     *
     * Expected header:
     * Date,Type,Description,Status,Amount
     *
     * Example rows:
     * "2025-08-29","","Current Year Individual Contribution","Processed",$290.38
     * "2025-08-27","","253-SALE (DEBIT)-...","Processed",-$203.46
     */
    public static List<HSARecord> readTransactionsFromCsv(Path csvPath) throws IOException {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Stable wrap to preserve original order for equal dates
        class RowWrap {
            final int originalIndex;
            final HSARecord rec;
            RowWrap(int originalIndex, HSARecord rec) {
                this.originalIndex = originalIndex;
                this.rec = rec;
            }
        }

        List<RowWrap> rows = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(csvPath)) {
            String line;
            int lineNo = 0;
            int txnIndex = 0;

            while ((line = br.readLine()) != null) {
                lineNo++;

                if (lineNo == 1) {
                    // header
                    continue;
                }

                if (line.trim().isEmpty()) continue;

                List<String> cols = parseCsvLine(line);
                if (cols.size() < 5) continue;

                String dateStr = stripQuotes(cols.get(0)).trim();
                String type = stripQuotes(cols.get(1)).trim();        // not always present
                String desc = stripQuotes(cols.get(2)).trim();
                String status = stripQuotes(cols.get(3)).trim();      // "Processed" in your sample
                String amtStr = stripQuotes(cols.get(4)).trim();

                if (dateStr.isEmpty()) continue;

                LocalDate txDate = LocalDate.parse(dateStr, dtf);
                double amt = parseMoney(amtStr);

                // Build a clean description (keep it close to bank statement text)
                String finalDesc = desc;
                if (!type.isEmpty()) {
                    finalDesc = type + " - " + finalDesc;
                }
                // If status is something other than Processed, keep it visible
                if (!status.isEmpty() && !"Processed".equalsIgnoreCase(status)) {
                    finalDesc = finalDesc + " (Status: " + status + ")";
                }

                HSARecord r = new HSARecord();
                r.setTransactionDate(txDate);
                r.setDescription(finalDesc);
                r.setAmount(amt);
                r.setBalance(0.0); // CSV does not provide running balance

                rows.add(new RowWrap(txnIndex++, r));
            }
        }

        // Sort old -> new; stable for same date
        rows.sort(Comparator
                .comparing((RowWrap w) -> w.rec.getTransactionDate())
                .thenComparingInt(w -> w.originalIndex));

        List<HSARecord> out = new ArrayList<>(rows.size());
        for (RowWrap w : rows) out.add(w.rec);
        return out;
    }

    /**
     * Minimal CSV parser that supports quotes and commas inside quotes.
     */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;

        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // Handle doubled quotes inside quoted text ("")
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

    private static String stripQuotes(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    /**
     * Converts money strings like "$290.38", "-$203.46", "1,183.02", "" into a double.
     */
    private static double parseMoney(String s) {
        if (s == null) return 0.0;
        String t = s.trim();
        if (t.isEmpty()) return 0.0;

        // remove surrounding quotes if any
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            t = t.substring(1, t.length() - 1).trim();
        }

        // remove commas + currency symbol
        t = t.replace(",", "").replace("$", "").trim();

        if (t.isEmpty()) return 0.0;
        return Double.parseDouble(t);
    }
}
