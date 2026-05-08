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

import org.kumar.excel.util.LedgerRecord;

public class ChaseCheckingRecord implements LedgerInterface {

	/**
	 * Record for Chase checking CSV exports that look like: Details,Posting
	 * Date,Description,Amount,Type,Balance,Check or Slip #
	 *
	 * Works with MonthlyLoad: - getTransactionDate() -> used to compute
	 * DateInterval - convertToLedgerRecord(accountName, order) -> used to add to
	 * ledger - readTransactionsFromCsv(Path) -> static reader returns sorted list
	 * old->new
	 */

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

	private String details; // e.g. CREDIT/DEBIT
	private LocalDate postingDate; // use this as "transactionDate"
	private String description;
	private double amount; // already signed in file (debits negative)
	private String type; // e.g. ACCT_XFER
	private double balance; // statement-provided running balance
	private String checkOrSlip; // optional

	public ChaseCheckingRecord() {
	}

	// --- getters/setters ---
	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	// MonthlyLoad expects this name (same as your other record classes)
	public LocalDate getTransactionDate() {
		return postingDate;
	}

	public void setTransactionDate(LocalDate d) {
		this.postingDate = d;
	}

	public LocalDate getPostingDate() {
		return postingDate;
	}

	public void setPostingDate(LocalDate postingDate) {
		this.postingDate = postingDate;
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

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public double getBalance() {
		return balance;
	}

	public void setBalance(double balance) {
		this.balance = balance;
	}

	public String getCheckOrSlip() {
		return checkOrSlip;
	}

	public void setCheckOrSlip(String checkOrSlip) {
		this.checkOrSlip = checkOrSlip;
	}

	@Override
	public String toString() {
		return "ChaseCheckingRecord{" + "details='" + details + '\'' + ", postingDate=" + postingDate
				+ ", description='" + description + '\'' + ", amount=" + amount + ", type='" + type + '\''
				+ ", balance=" + balance + ", checkOrSlip='" + checkOrSlip + '\'' + '}';
	}

	/**
	 * LedgerInterface implementation (same pattern as BOACheckingRecord /
	 * ChaseCCCsvRecord). We store the statement running balance into the
	 * LedgerRecord "balance" field.
	 */
	public LedgerRecord convertToLedgerRecord(String accountName, short order) {
		short year = (short) this.postingDate.getYear();
		String month = this.postingDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		Date transDate = Date.from(this.postingDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

		// LedgerRecord ctor signature in your project matches:
		// (account, order, year, month, transDate, description, amount, balance, ...)
		return new LedgerRecord(accountName, order, year, month, transDate, this.description, this.amount, this.balance,
				null, null, null, null, null);
	}

	/**
	 * Static reader that returns transactions sorted old -> new (stable for
	 * same-day rows).
	 */
	public static List<ChaseCheckingRecord> readTransactionsFromCsv(Path csvPath) throws IOException {

		class RowWrap {
			final int originalIndex;
			final ChaseCheckingRecord rec;

			RowWrap(int originalIndex, ChaseCheckingRecord rec) {
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
					headerSkipped = true; // skip header line
					continue;
				}
				if (line.trim().isEmpty())
					continue;

				List<String> cols = parseCsvLine(line);

				// Expected columns:
				// 0 Details, 1 Posting Date, 2 Description, 3 Amount, 4 Type, 5 Balance, 6
				// Check or Slip #
				if (cols.size() < 6)
					continue;

				ChaseCheckingRecord r = new ChaseCheckingRecord();
				r.setDetails(cols.get(0).trim());
				r.setPostingDate(LocalDate.parse(cols.get(1).trim(), DATE_FMT));
				r.setDescription(cols.get(2).trim());
				r.setAmount(parseMoney(cols.get(3)));
				r.setType(cols.get(4).trim());
				r.setBalance(parseMoney(cols.get(5)));
				if (cols.size() >= 7) {
					String c = cols.get(6).trim();
					r.setCheckOrSlip(c.isEmpty() ? null : c);
				}

				rows.add(new RowWrap(idx++, r));
			}
		}

		// old -> new, stable within same date
		rows.sort(Comparator.comparing((RowWrap w) -> w.rec.getPostingDate()).thenComparingInt(w -> w.originalIndex));

		List<ChaseCheckingRecord> out = new ArrayList<>(rows.size());
		for (RowWrap w : rows)
			out.add(w.rec);
		return out;
	}

	/**
	 * Minimal CSV parser (supports quotes + commas inside quotes).
	 */
	private static List<String> parseCsvLine(String line) {
		List<String> out = new ArrayList<>();
		if (line == null)
			return out;

		StringBuilder cur = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '"') {
				// Handle doubled quotes ("")
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

	/**
	 * Parses values like "2,400.00", "-2400.00", "$1,234.56", "" -> double
	 */
	private static double parseMoney(String s) {
		if (s == null)
			return 0.0;
		String t = s.trim();
		if (t.isEmpty())
			return 0.0;

		if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
			t = t.substring(1, t.length() - 1).trim();
		}

		t = t.replace(",", "").replace("$", "").trim();
		if (t.isEmpty())
			return 0.0;

		return Double.parseDouble(t);
	}
}