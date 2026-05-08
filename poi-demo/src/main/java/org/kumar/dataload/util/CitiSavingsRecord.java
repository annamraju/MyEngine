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

public class CitiSavingsRecord implements LedgerInterface {

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd-yyyy");

	private String status; // Cleared, etc.
	private LocalDate transactionDate; // from "Date"
	private String description;
	private double debit; // if present
	private double credit; // if present
	private double amount; // derived: credit - debit (so debit => negative)
	private double balance; // not present in this CSV; kept for consistency (defaults 0)

	public CitiSavingsRecord() {
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	public double getDebit() {
		return debit;
	}

	public void setDebit(double debit) {
		this.debit = debit;
	}

	public double getCredit() {
		return credit;
	}

	public void setCredit(double credit) {
		this.credit = credit;
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
		return "CitiSavingsRecord{" + "status='" + status + '\'' + ", transactionDate=" + transactionDate
				+ ", description='" + description + '\'' + ", debit=" + debit + ", credit=" + credit + ", amount="
				+ amount + ", balance=" + balance + '}';
	}

	/**
	 * Same LedgerRecord ctor pattern as BOACheckingRecord / ChaseCCCsvRecord. Note:
	 * this CSV doesn't provide per-row balance, so we write 0.0 (same as BOA does).
	 * :contentReference[oaicite:2]{index=2}
	 */
	public LedgerRecord convertToLedgerRecord(String accountName, short order) {
		short year = (short) this.transactionDate.getYear();
		String month = this.transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		Date transDate = Date.from(this.transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

		return new LedgerRecord(accountName, order, year, month, transDate, this.description, this.amount, 0.0, // balance
																												// field
																												// in
																												// LedgerRecord
																												// (not
																												// available
																												// here)
				null, null, null, null, null);
	}

	/**
	 * Reads SAV_6415_CURRENT_VIEW.csv and returns transactions sorted old -> new.
	 *
	 * CSV columns (observed): Status,Date,Description,Debit,Credit,(optional
	 * trailing empty) Date format: MM-dd-yyyy
	 */
	public static List<CitiSavingsRecord> readTransactionsFromCsv(Path csvPath) throws IOException {

		class RowWrap {
			final int originalIndex;
			final CitiSavingsRecord rec;

			RowWrap(int originalIndex, CitiSavingsRecord rec) {
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

				// skip header
				if (!headerSkipped) {
					headerSkipped = true;
					continue;
				}

				if (line.trim().isEmpty())
					continue;

				List<String> cols = parseCsvLine(line);

				// Need at least: Status, Date, Description
				if (cols.size() < 3)
					continue;

				String status = safeGet(cols, 0).trim();
				String dateStr = safeGet(cols, 1).trim();
				String desc = safeGet(cols, 2).trim();

				String debitStr = (cols.size() >= 4) ? safeGet(cols, 3) : "";
				String creditStr = (cols.size() >= 5) ? safeGet(cols, 4) : "";

				double debit = parseMoney(debitStr);
				double credit = parseMoney(creditStr);

				// Your business rule: debit -> negative, credit -> positive
				double amount = credit - debit;

				CitiSavingsRecord r = new CitiSavingsRecord();
				r.setStatus(status);
				r.setTransactionDate(LocalDate.parse(dateStr, DATE_FMT));
				r.setDescription(desc);
				r.setDebit(debit);
				r.setCredit(credit);
				r.setAmount(amount);
				r.setBalance(0.0);

				rows.add(new RowWrap(idx++, r));
			}
		}

		// Sort old -> new; stable order for same-date items
		rows.sort(
				Comparator.comparing((RowWrap w) -> w.rec.getTransactionDate()).thenComparingInt(w -> w.originalIndex));

		List<CitiSavingsRecord> out = new ArrayList<>(rows.size());
		for (RowWrap w : rows)
			out.add(w.rec);
		return out;
	}

	private static String safeGet(List<String> cols, int i) {
		return (i >= 0 && i < cols.size() && cols.get(i) != null) ? cols.get(i) : "";
	}

	/**
	 * CSV parser supporting quotes and commas inside quotes.
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
				// handle escaped quotes ("")
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
	 * Parses money like "1,234.56", "-12.00", "$5.00", "" -> double. Note: in this
	 * CSV debit/credit columns are positive numbers, so we keep them positive.
	 */
	private static double parseMoney(String s) {
		if (s == null)
			return 0.0;
		String t = s.trim();
		if (t.isEmpty())
			return 0.0;

		// strip surrounding quotes
		if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
			t = t.substring(1, t.length() - 1).trim();
		}

		t = t.replace(",", "").replace("$", "").trim();
		if (t.isEmpty())
			return 0.0;

		return Double.parseDouble(t);
	}
}
