package org.kumar.dataload.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BOACheckingRecordTest {

    @TempDir
    Path tempDir;

    @Test
    void readTransactionsFromCsvAcceptsUnpaddedAndPaddedDates() throws Exception {
        Path csv = tempDir.resolve("stmt.csv");
        Files.writeString(csv, String.join("\n",
                "Description,,Summary Amt.",
                "Beginning balance as of 12/01/2025,,\"1,000.00\"",
                "",
                "Date,Description,Amount,Running Bal.",
                "1/2/2026,\"Zelle payment\",\"-50.00\",\"950.00\"",
                "01/15/2026,\"Direct Deposit\",\"100.00\",\"1,050.00\"",
                "12/3/2025,\"Grocery\",\"-25.00\",\"975.00\""));

        List<BOACheckingRecord> records = BOACheckingRecord.readTransactionsFromCsv(csv);

        assertEquals(3, records.size());
        assertEquals(LocalDate.of(2025, 12, 3), records.get(0).getTransactionDate());
        assertEquals(LocalDate.of(2026, 1, 2), records.get(1).getTransactionDate());
        assertEquals(LocalDate.of(2026, 1, 15), records.get(2).getTransactionDate());
        assertEquals(-25.0, records.get(0).getAmount(), 0.001);
        assertEquals(-50.0, records.get(1).getAmount(), 0.001);
        assertEquals(100.0, records.get(2).getAmount(), 0.001);
    }
}
