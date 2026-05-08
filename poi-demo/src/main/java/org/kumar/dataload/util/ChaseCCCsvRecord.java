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

public class ChaseCCCsvRecord implements LedgerInterface{

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private LocalDate transactionDate;
    private LocalDate postDate;
    private String description;
    private String category;
    private String type;
    private double amount;
    private String memo;

    public ChaseCCCsvRecord() {
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public LocalDate getPostDate() {
        return postDate;
    }

    public void setPostDate(LocalDate postDate) {
        this.postDate = postDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    @Override
    public String toString() {
        return "ChaseCsvRecord{" +
                ", transactionDate=" + transactionDate +
                ", postDate=" + postDate +
                ", description='" + description + '\'' +
                ", category='" + category + '\'' +
                ", type='" + type + '\'' +
                ", amount=" + amount +
                ", memo='" + memo + '\'' +
                '}';
    }
    
    public LedgerRecord convertToLedgerRecord(String accountName, short order) {
		short year = (short)(this.transactionDate.getYear());
		String month = this.transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
		Date transDate =  Date.from(this.transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
		
		LedgerRecord rec = new LedgerRecord(accountName,
				order,
				year,
				month,
				transDate,
				this.description,
				this.amount,
				0.0,
				null,
				null,
				null,
				null,
				null);
		
		return rec;
	}
    
    public static List<ChaseCCCsvRecord> readTransactionsFromCsv(Path csvFile) throws IOException {

        List<ChaseCCCsvRecord> records = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(csvFile)) {

            String line;
            boolean headerSkipped = false;

            while ((line = br.readLine()) != null) {

                // skip header
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }

                // simple CSV split (safe for your file)
                String[] cols = line.split(",", -1);

                ChaseCCCsvRecord r = new ChaseCCCsvRecord();

                r.setTransactionDate(LocalDate.parse(cols[0].trim(), DATE_FMT));
                r.setPostDate(LocalDate.parse(cols[1].trim(), DATE_FMT));
                r.setDescription(cols[2].trim());
                r.setCategory(cols[3].trim());
                r.setType(cols[4].trim());
                r.setAmount(Double.parseDouble(cols[5].trim()));
                r.setMemo(cols.length > 6 ? cols[6].trim() : null);

                records.add(r);
            }
        }

        records.sort(Comparator.comparing(ChaseCCCsvRecord::getTransactionDate));
        
        return records;
    }
    
}
