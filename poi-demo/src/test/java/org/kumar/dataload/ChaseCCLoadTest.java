package org.kumar.dataload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kumar.dataload.util.DataLoadJsonStore;

class ChaseCCLoadTest {

    @TempDir
    Path tempDir;

    @Test
    void processTransactionFileLoadsMultiCycleCsvOnceAndMovesOnce() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Path processedDir = tempDir.resolve("processed");
        Files.createDirectories(sourceDir);
        Files.createDirectories(processedDir);

        // Transaction dates span two Chase CC cycles (15th->14th):
        // 2026-03-14 -> 2026-02-15..2026-03-14
        // 2026-03-15 -> 2026-03-15..2026-04-14
        Path csv = sourceDir.resolve("Chase6288_Activity20260315_20260414_20260701.CSV");
        Files.writeString(csv, String.join("\n",
                "Transaction Date,Post Date,Description,Category,Type,Amount,Memo",
                "03/14/2026,03/15/2026,Earlier purchase,Food,Sale,-10.00,",
                "03/15/2026,03/16/2026,Cycle purchase,Food,Sale,-20.00,",
                "04/01/2026,04/02/2026,Later purchase,Food,Sale,-30.00,"));

        Path configFile = tempDir.resolve("dataload.json");
        Files.write(configFile, "{ \"accounts\": [] }\n".getBytes(StandardCharsets.UTF_8));
        DataLoadJsonStore store = DataLoadJsonStore.loadFromResourceToConfig(
                "dataload.json", configFile, 5);

        ChaseCCLoad load = new ChaseCCLoad();
        load.setStore(store);
        load.setLedger(new ArrayList<>());
        load.setProcessedFolder(processedDir.toString());

        load.processTransactionFile(csv);

        assertEquals(3, load.getLedgerRecordCount());
        assertEquals(1, store.findIntervalOrThrow(
                "Chase CC",
                LocalDate.of(2026, 2, 15),
                LocalDate.of(2026, 3, 14)).numberOfRecords);
        assertEquals(2, store.findIntervalOrThrow(
                "Chase CC",
                LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 14)).numberOfRecords);

        assertTrue(Files.notExists(csv), "source file should be moved once");
        assertTrue(Files.exists(processedDir.resolve(csv.getFileName())));
    }
}
