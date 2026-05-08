package org.kumar.dataload.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ChaseCCAnanthaCsvReader {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy");

    public static List<ChaseCCananthaCsvRecord> read(Path csvFile) throws IOException {

        List<ChaseCCananthaCsvRecord> records = new ArrayList<>();

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

                ChaseCCananthaCsvRecord r = new ChaseCCananthaCsvRecord();

                r.setCard(Long.parseLong(cols[0].trim()));
                r.setTransactionDate(LocalDate.parse(cols[1].trim(), DATE_FMT));
                r.setPostDate(LocalDate.parse(cols[2].trim(), DATE_FMT));
                r.setDescription(cols[3].trim());
                r.setCategory(cols[4].trim());
                r.setType(cols[5].trim());
                r.setAmount(Double.parseDouble(cols[6].trim()));
                r.setMemo(cols.length > 7 ? cols[7].trim() : null);

                records.add(r);
            }
        }

        return records;
    }
}
