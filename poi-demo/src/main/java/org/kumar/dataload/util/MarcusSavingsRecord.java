package org.kumar.dataload.util;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.kumar.excel.util.LedgerRecord;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Reader for Marcus / Goldman Sachs Online Savings PDF statements.
 *
 * Statement has an "ACCOUNT ACTIVITY" section with columns:
 * Date | Description | Credits | Debits | Balance
 *
 * Example lines from your PDF:
 * 11/03/2025 ACH Deposit Internet transfer from ...
 * $3,600.00 $233,908.03
 *
 * 11/30/2025 Interest Paid $738.14 $290,987.46
 *
 * This parser is robust to wrapped description lines.
 */
public class MarcusSavingsRecord implements LedgerInterface {

    private LocalDate transactionDate;
    private String description;
    /** Positive for credits, negative for debits */
    private double amount;
    private double balance;

    public MarcusSavingsRecord() {}

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    @Override
    public String toString() {
        return "MarcusSavingsRecord{" +
                "transactionDate=" + transactionDate +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                ", balance=" + balance +
                '}';
    }

    /**
     * LedgerInterface implementation (same approach as BOACheckingRecord).
     */
    @Override
    public LedgerRecord convertToLedgerRecord(String accountName, short order) {
        short year = (short) this.transactionDate.getYear();
        String month = this.transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        Date transDate = Date.from(this.transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        // LedgerRecord signature used in your BOACheckingRecord:
        // new LedgerRecord(accountName, order, year, month, transDate, description, amount, 0.0, ...)
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
     * Main entry point for MonthlyLoad: parse the PDF and return activity rows as records.
     */
    public static List<MarcusSavingsRecord> readTransactionsFromPdf(Path pdfPath) throws IOException {
        String text = extractText(pdfPath);

        // Normalize line endings and split.
        String[] rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = (l == null) ? "" : l.trim();
            if (!t.isEmpty()) lines.add(t);
        }

        // Find ACCOUNT ACTIVITY section and then the header line.
        int start = indexOfContains(lines, "ACCOUNT ACTIVITY");
        if (start < 0) {
            throw new IOException("Could not find 'ACCOUNT ACTIVITY' section in PDF: " + pdfPath);
        }

        // header usually: "Date Description Credits Debits Balance"
        int header = -1;
        for (int i = start; i < lines.size(); i++) {
            String l = lines.get(i);
            if (l.toLowerCase(Locale.ROOT).contains("date")
                    && l.toLowerCase(Locale.ROOT).contains("description")
                    && l.toLowerCase(Locale.ROOT).contains("balance")) {
                header = i;
                break;
            }
        }
        if (header < 0) {
            throw new IOException("Could not find activity table header after ACCOUNT ACTIVITY in PDF: " + pdfPath);
        }

        // Parse rows until we hit "ACCOUNT ACTIVITY (continued)" end-of-table markers,
        // or "Ending Balance", or error/boilerplate section.
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        Pattern dateAtStart = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4})\\s+(.*)$");
        Pattern money = Pattern.compile("\\$\\s?([0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)");

        List<MarcusSavingsRecord> out = new ArrayList<>();

        LocalDate curDate = null;
        StringBuilder curDesc = new StringBuilder();

        // We accumulate text until we can detect:
        // - transaction amount (credit/debit) AND
        // - balance
        // Most lines contain two $ amounts: {txnAmount} {balance}.
        // Some “Beginning Balance” / “Ending Balance” lines are special; we skip them.
        for (int i = header + 1; i < lines.size(); i++) {
            String l = lines.get(i);

            // Stop conditions / noise
            String lower = l.toLowerCase(Locale.ROOT);
            if (lower.startsWith("in case of errors")
                    || lower.startsWith("if you think your statement")
                    || lower.contains("deposit account agreement")) {
                break;
            }

            // Ignore repeated headers on continued pages
            if (lower.contains("account activity") && lower.contains("continued")) {
                continue;
            }
            if (lower.contains("date") && lower.contains("description") && lower.contains("balance")) {
                continue;
            }

            Matcher dm = dateAtStart.matcher(l);
            if (dm.find()) {
                // We encountered a new date row.
                // First, try to flush any pending txn (if we have one).
            	boolean added = flushIfComplete(out, curDate, curDesc.toString(), money);
            	if (added) {
            	    curDate = null;
            	    curDesc.setLength(0);
            	}

                curDate = LocalDate.parse(dm.group(1), dtf);
                curDesc.setLength(0);
                curDesc.append(dm.group(2).trim());
                continue;
            }

            // Continuation line of description OR amounts line
            if (curDate != null) {
                // Append with a space (handles wrapped text like "UNION DDA account ....")
                curDesc.append(" ").append(l.trim());

                // Try flush if we now have enough money tokens
                boolean added = flushIfComplete(out, curDate, curDesc.toString(), money);
                if (added) {
                    curDate = null;
                    curDesc.setLength(0);
                }
            }
        }

        // last pending
        flushIfComplete(out, curDate, curDesc.toString(), money);

        return out;
    }

    private static boolean flushIfComplete(List<MarcusSavingsRecord> out,
            LocalDate date,
            String descWithAmounts,
            Pattern moneyPattern) {
			if (date == null) return false;
			
			String d = descWithAmounts.trim();
			if (d.isEmpty()) return false;
			
			List<Double> amounts = new ArrayList<>();
			Matcher m = moneyPattern.matcher(d);
			while (m.find()) amounts.add(parseMoney(m.group(1)));
			
			String lower = d.toLowerCase(Locale.ROOT);
			if (lower.contains("beginning balance") || lower.contains("ending balance")) return false;
			
			if (amounts.size() < 2) return false;
			
			double txnAmt = amounts.get(0);
			double bal = amounts.get(1);
			
			String cleanDesc = d.replaceAll("\\$\\s?[0-9]{1,3}(?:,[0-9]{3})*(?:\\.[0-9]{2})?", "").trim();
			cleanDesc = cleanDesc.replaceAll("\\s{2,}", " ");
			
			boolean isDebit = cleanDesc.toLowerCase(Locale.ROOT).contains("withdrawal");
			double signedAmt = isDebit ? -Math.abs(txnAmt) : Math.abs(txnAmt);
			
			MarcusSavingsRecord r = new MarcusSavingsRecord();
			r.setTransactionDate(date);
			r.setDescription(cleanDesc);
			r.setAmount(signedAmt);
			r.setBalance(bal);
			
			out.add(r);
			return true;
}

    private static boolean nearlyEqual(double a, double b) {
        return Math.abs(a - b) < 0.0001;
    }

    private static int indexOfContains(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return -1;
    }

    private static String extractText(Path pdfPath) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            // If you later notice ordering issues, you can try:
            // stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

    /** Converts money strings like "65,591.29" into double. */
    private static double parseMoney(String s) {
        if (s == null) return 0.0;
        String t = s.trim().replace(",", "");
        if (t.isEmpty()) return 0.0;
        return Double.parseDouble(t);
    }

    /**
     * Quick manual test.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: MarcusSavingsRecord <statement.pdf>");
            return;
        }

        List<MarcusSavingsRecord> recs = readTransactionsFromPdf(Path.of(args[0]));
        for (MarcusSavingsRecord r : recs) {
            System.out.println(r);
        }
    }
}
