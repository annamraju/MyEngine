package org.kumar.dataload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kumar.dataload.util.DataLoadJsonStore;
import org.kumar.dataload.util.EtradeBrokerageRecord;
import org.kumar.excel.util.LedgerRecord;

class MonthlyLoadTest {

    @TempDir
    Path tempDir;

    @Test
    void processTransactionFileCountsRecordsPerCalendarMonth() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Path processedDir = tempDir.resolve("processed");
        Files.createDirectories(sourceDir);
        Files.createDirectories(processedDir);

        Path statement = sourceDir.resolve("statement.txt");
        Files.writeString(statement, "placeholder");

        Path configFile = tempDir.resolve("dataload.json");
        Files.write(configFile, "{ \"accounts\": [] }\n".getBytes(StandardCharsets.UTF_8));
        DataLoadJsonStore store = DataLoadJsonStore.loadFromResourceToConfig(
                "dataload.json", configFile, 5);

        List<EtradeBrokerageRecord> statementTxns = new ArrayList<>();
        statementTxns.add(txn(LocalDate.of(2019, 5, 31), "May trade"));
        for (int i = 1; i <= 63; i++) {
            statementTxns.add(txn(LocalDate.of(2019, 6, Math.min(i, 28)), "June trade " + i));
        }

        MonthlyLoad<EtradeBrokerageRecord> load = new MonthlyLoad<>(
                "etrade Brokerage",
                sourceDir.toString(),
                ".txt",
                processedDir.toString(),
                path -> statementTxns,
                EtradeBrokerageRecord::getTransactionDate,
                (rec, order) -> new LedgerRecord(
                        "etrade Brokerage",
                        order,
                        (short) rec.getTransactionDate().getYear(),
                        rec.getTransactionDate().getMonth().name(),
                        java.sql.Date.valueOf(rec.getTransactionDate()),
                        rec.getDescription(),
                        rec.getAmount(),
                        0.0,
                        null, null, null, null, null)
        );
        load.setStore(store);
        load.setLedger(new ArrayList<>());
        load.setAutoSaveJson(false);

        load.processTransactionFile(statement);

        assertEquals(1, store.findIntervalOrThrow(
                "etrade Brokerage",
                LocalDate.of(2019, 5, 1),
                LocalDate.of(2019, 5, 31)).numberOfRecords);
        assertEquals(63, store.findIntervalOrThrow(
                "etrade Brokerage",
                LocalDate.of(2019, 6, 1),
                LocalDate.of(2019, 6, 30)).numberOfRecords);

        assertEquals(64, load.getLedgerRecordCount());
        assertTrue(Files.notExists(statement));
        assertTrue(Files.exists(processedDir.resolve("statement.txt")));
    }

    private static EtradeBrokerageRecord txn(LocalDate date, String description) {
        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(date);
        rec.setDescription(description);
        rec.setAmount(-1.0);
        rec.setSection("Securities");
        rec.setTransactionType("Bought");
        return rec;
    }
}
