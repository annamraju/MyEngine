package org.kumar.dataload.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.kumar.excel.util.LedgerRecord;

public class BofaCsvToObjects{

	// 1) Predefined structure
	public static class Txn {
		private final LocalDate postedDate;
		private final String referenceNumber;
		private final String payee;
		private final String address;
		private final BigDecimal amount;
		private final static String account = "BOA CC";

		public Txn(LocalDate postedDate, String referenceNumber, String payee, String address, BigDecimal amount) {
			this.postedDate = postedDate;
			this.referenceNumber = referenceNumber;
			this.payee = payee;
			this.address = address;
			this.amount = amount;
		}

		public LocalDate getPostedDate() {
			return postedDate;
		}

		public String getReferenceNumber() {
			return referenceNumber;
		}

		public String getPayee() {
			return payee;
		}

		public String getAddress() {
			return address;
		}

		public BigDecimal getAmount() {
			return amount;
		}

		public LedgerRecord covnertToLedgerRecord(short order1) {
			short year = (short)(postedDate.getYear());
			String month = postedDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
			Date transDate =  Date.from(postedDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
			double amt = amount.doubleValue();
			
			LedgerRecord rec = new LedgerRecord(account,
					order1,
					year,
					month,
					transDate,
					this.payee,
					amt,
					0.0,
					null,
					null,
					null,
					null,
					null);
			
			return rec;
		}
		
		@Override
		public String toString() {
			return "Txn{" + "postedDate=" + postedDate + ", referenceNumber='" + referenceNumber + '\'' + ", payee='"
					+ payee + '\'' + ", address='" + address + '\'' + ", amount=" + amount + '}';
		}
	}

	// 2) Main read method -> returns an array of Txn
	public static Txn[] readTxns(Path csvPath) throws IOException {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd/yyyy");
		List<Txn> out = new ArrayList<>();

		try (BufferedReader br = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
			String line = br.readLine(); // header
			if (line == null)
				return new Txn[0];

			while ((line = br.readLine()) != null) {
				if (line.trim().isEmpty())
					continue;

				List<String> cols = parseCsvLine(line);

				// Expected columns: Posted Date, Reference Number, Payee, Address, Amount
				// Guard for short lines
				while (cols.size() < 5)
					cols.add("");

				LocalDate postedDate = LocalDate.parse(cols.get(0).trim(), dtf);
				String ref = cols.get(1).trim();
				String payee = cols.get(2).trim();
				String address = cols.get(3).trim();
				BigDecimal amount = new BigDecimal(cols.get(4).trim());

				out.add(new Txn(postedDate, ref, payee, address, amount));
			}
		}

		return out.toArray(new Txn[0]);
	}

	// 3) Simple CSV parser: supports commas inside quotes + escaped quotes ("")
	private static List<String> parseCsvLine(String line) {
		List<String> result = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);

			if (c == '"') {
				// If we are in quotes and next char is also a quote -> it's an escaped quote
				if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
					cur.append('"');
					i++;
				} else {
					inQuotes = !inQuotes;
				}
			} else if (c == ',' && !inQuotes) {
				result.add(cur.toString());
				cur.setLength(0);
			} else {
				cur.append(c);
			}
		}
		result.add(cur.toString());
		return result;
	}

	// 4) Demo
	public static void main(String[] args) throws Exception {
		Path p = Path.of("August2025_9504 (1).csv"); // change path as needed
		Txn[] txns = readTxns(p);

		System.out.println("Loaded: " + txns.length);
		for (int i = 0; i < Math.min(txns.length, 5); i++) {
			System.out.println(txns[i]);
		}
	}
}
