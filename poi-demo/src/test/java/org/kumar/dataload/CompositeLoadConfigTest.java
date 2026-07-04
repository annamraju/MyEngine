package org.kumar.dataload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class CompositeLoadConfigTest {

    @Test
    void loadDefaultFromClasspath() throws Exception {
        CompositeLoadConfig config = CompositeLoadConfig.loadDefault();

        assertNotNull(config.ledgerFile);
        assertEquals("Runbook", config.sheetName);
        assertNotNull(config.outputFile);
        assertNotNull(config.baselineInputDir);
        assertEquals("\\Processed", config.baselineProcessedDir);
        assertEquals("ledger-completeness-accounts.json", config.accountStatusConfig);
        assertEquals("dataload.json", config.dataloadJson);
    }
}
