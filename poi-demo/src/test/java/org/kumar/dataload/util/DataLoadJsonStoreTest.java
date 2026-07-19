package org.kumar.dataload.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataLoadJsonStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveTwiceInSameSecondDoesNotThrowOnBackupCollision() throws Exception {
        Path configFile = tempDir.resolve("dataload.json");
        Files.write(configFile, "{ \"accounts\": [] }\n".getBytes(StandardCharsets.UTF_8));

        DataLoadJsonStore store = DataLoadJsonStore.loadFromResourceToConfig(
                "dataload.json", configFile, 20);

        LocalDate start = LocalDate.of(2019, 5, 1);
        LocalDate end = LocalDate.of(2019, 5, 31);

        store.upsertInterval("etrade Brokerage", start, end, "Loaded", 10);

        assertDoesNotThrow(() -> {
            store.save();
            // Second save in the same second previously failed with FileAlreadyExistsException
            // because backup names used second precision and CREATE_NEW.
            store.upsertInterval("etrade Brokerage",
                    LocalDate.of(2019, 6, 1),
                    LocalDate.of(2019, 6, 30),
                    "Loaded",
                    12);
            store.save();
            store.save();
        });

        Path backupDir = tempDir.resolve("backups");
        assertTrue(Files.isDirectory(backupDir));

        List<Path> backups = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(backupDir, "dataload.json.*.bak")) {
            for (Path p : ds) {
                backups.add(p);
            }
        }
        assertTrue(backups.size() >= 2, "expected at least two backups, found " + backups.size());
    }
}
