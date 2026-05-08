package org.kumar.dataload.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChaseCCCsvReader {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy");

    public static List<ChaseCCCsvRecord> read(Path csvFile) throws IOException {

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
