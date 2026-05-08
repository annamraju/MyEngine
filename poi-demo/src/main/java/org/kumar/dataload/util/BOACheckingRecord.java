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

public class BOACheckingRecord implements LedgerInterface{
	private LocalDate transactionDate;
	private String description;
	private double amount;
	private double balance;
	
	public BOACheckingRecord() {
	}

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

	public void setBalance(double amount) {
		this.balance = amount;
	}

	@Override
	public String toString() {
		return "BOACheckingRecord{" + ", transactionDate=" + transactionDate +  ", description='"
				+ description  + ", amount="
				+ amount + ", balance=" + balance  + '}';
	}

	/**
	 * LedgerInterface implementation
	 */
	public LedgerRecord convertToLedgerRecord(String accountName, short order) {
		short year = (short) (this.transactionDate.getYear());
		String month = this.transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		Date transDate = Date.from(this.transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

		LedgerRecord rec = new LedgerRecord(accountName, order, year, month, transDate, this.description, this.amount,
				0.0, null, null, null, null, null);

		return rec;
	}
	
	/**
	 * Reader
	 * @param csvPath
	 * @return
	 * @throws IOException
	 */
	public static List<BOACheckingRecord> readTransactionsFromCsv(Path csvPath) throws IOException {
	    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd/yyyy");

	    double beginningBalance = 0.0;

	    // We'll first load all "transaction" rows (lines below the Date,Description,Amount... header)
	    // but defer running-balance calculation until after sorting.
	    class RowWrap {
	        final int originalIndex;
	        final BOACheckingRecord rec;
	        RowWrap(int originalIndex, BOACheckingRecord rec) {
	            this.originalIndex = originalIndex;
	            this.rec = rec;
	        }
	    }

	    List<RowWrap> rows = new ArrayList<>();

	    try (BufferedReader br = Files.newBufferedReader(csvPath)) {
	        String line;
	        int lineNo = 0;
	        boolean inTxnSection = false;
	        int txnIndex = 0;

	        while ((line = br.readLine()) != null) {
	            lineNo++;

	            // Line 2 has beginning balance summary in your file:
	            // Beginning balance as of 12/01/2025,,"1,183.02"
	            if (lineNo == 2) {
	                List<String> cols = parseCsvLine(line);
	                // expect: [Description, "", Summary Amt.]
	                // but line 2: [Beginning balance..., "", 1,183.02]
	                if (cols.size() >= 3) {
	                    beginningBalance = parseMoney(cols.get(2));
	                }
	                continue;
	            }

	            // Find the transaction header line:
	            // Date,Description,Amount,Running Bal.
	            if (!inTxnSection) {
	                if (line.startsWith("Date,Description,Amount")) {
	                    inTxnSection = true;
	                }
	                continue;
	            }

	            // Now we are in transaction rows (lines 8-25 in your sample)
	            if (line.trim().isEmpty()) continue;

	            List<String> cols = parseCsvLine(line);
	            // Expect: Date, Description, Amount, Running Bal.
	            if (cols.size() < 2) continue;

	            String dateStr = cols.get(0).trim();
	            String desc = cols.get(1).trim();
	            String amtStr = (cols.size() >= 3) ? cols.get(2) : "";

	            if(desc.startsWith("Beginning balance as of")) continue;
	            
	            LocalDate txDate = LocalDate.parse(dateStr, dtf);
	            double amt = parseMoney(amtStr); // empty amount => 0.0

	            BOACheckingRecord r = new BOACheckingRecord();
	            r.setTransactionDate(txDate);
	            r.setDescription(desc);
	            r.setAmount(amt);

	            rows.add(new RowWrap(txnIndex++, r));
	        }
	    }

	    // Sort old -> new, but keep original order for same date (stable tie-breaker)
	    rows.sort(Comparator
	            .comparing((RowWrap w) -> w.rec.getTransactionDate())
	            .thenComparingInt(w -> w.originalIndex));

	    // Compute running balance starting from the beginning balance (line 2)
	    double bal = beginningBalance;
	    for (RowWrap w : rows) {
	        bal += w.rec.getAmount();
	        w.rec.setBalance(bal);
	    }

	    // Return just the records
	    List<BOACheckingRecord> out = new ArrayList<>(rows.size());
	    for (RowWrap w : rows) out.add(w.rec);
	    return out;
	}

	/**
	 * Minimal CSV parser that supports quotes and commas inside quotes.
	 * Example: 12/03/2025,"Zelle payment to ... ""anjalis ...""", "-350.00","1,136.39"
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
	                i++; // skip next quote
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

	/** Converts money strings like "1,183.02", "-1,209.12", "" into a double. */
	private static double parseMoney(String s) {
	    if (s == null) return 0.0;
	    String t = s.trim();
	    if (t.isEmpty()) return 0.0;

	    // remove surrounding quotes if any
	    if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
	        t = t.substring(1, t.length() - 1).trim();
	    }

	    // remove commas
	    t = t.replace(",", "");

	    // Some CSVs might include $; safe to strip
	    t = t.replace("$", "").trim();

	    if (t.isEmpty()) return 0.0;
	    return Double.parseDouble(t);
	}
}
