package org.kumar.dataload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kumar.dataload.util.EtradeBrokerageRecord;
import org.kumar.excel.util.LedgerRecord;
import org.kumar.rules.LedgerRecordCategorizer;
import org.kumar.rules.LedgerRecordCategorizer.Engine;

class EtradeBrokerageLoadTest {

    private static Engine categorizer;

    @BeforeAll
    static void loadCategorizer() throws Exception {
        categorizer = LedgerRecordCategorizer.load(
                "merchant_map.json",
                "rules.json",
                "check_rules.json"
        );
    }

    @Test
    void categorizeRecordAssignsSecuritiesPurchaseCategory() {
        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(java.time.LocalDate.of(2019, 5, 31));
        rec.setDescription("NVIDIA CORP NVDA");
        rec.setAmount(-2762.55);

        LedgerRecord ledgerRecord = EtradeBrokerageLoad.categorizeRecord(categorizer, rec, "etrade Brokerage", (short) 1);

        assertEquals("Investment", ledgerRecord.getCategory());
        assertEquals("Securities", ledgerRecord.getSub1());
        assertEquals("Etrade", ledgerRecord.getSub2());
        assertEquals(11266, ledgerRecord.getRuleNo());
    }

    @Test
    void categorizeRecordAssignsDividendCategory() {
        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(java.time.LocalDate.of(2019, 6, 3));
        rec.setDescription("Dividend - INTEL CORP CASH DIV ON 50 SHS REC 05/07/19 PAY 06/01/19 INTC");
        rec.setAmount(15.75);

        LedgerRecord ledgerRecord = EtradeBrokerageLoad.categorizeRecord(categorizer, rec, "etrade Brokerage", (short) 1);

        assertEquals("Income", ledgerRecord.getCategory());
        assertEquals("Investment Returns", ledgerRecord.getSub1());
        assertEquals("Securities", ledgerRecord.getSub2());
        assertEquals("Etrade", ledgerRecord.getSub3());
        assertEquals(11264, ledgerRecord.getRuleNo());
    }

    @Test
    void categorizeRecordAssignsExternalTransferCategory() {
        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(java.time.LocalDate.of(2019, 6, 10));
        rec.setDescription("Transfer - TRANSFER TO XXXXXX7162 REFID:24578471482;");
        rec.setAmount(-221.00);

        LedgerRecord ledgerRecord = EtradeBrokerageLoad.categorizeRecord(categorizer, rec, "etrade Brokerage", (short) 1);

        assertEquals("Internal", ledgerRecord.getCategory());
        assertEquals("Transfer", ledgerRecord.getSub1());
        assertEquals("External", ledgerRecord.getSub2());
        assertEquals(11261, ledgerRecord.getRuleNo());
    }

    @Test
    void categorizeRecordAssignsMarginInterestCategory() {
        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(java.time.LocalDate.of(2019, 6, 26));
        rec.setDescription("Interest - FROM 05/26 THRU 06/25 @10 1/2% BAL 41,092 AVBAL 27,417");
        rec.setAmount(-247.90);

        LedgerRecord ledgerRecord = EtradeBrokerageLoad.categorizeRecord(categorizer, rec, "etrade Brokerage", (short) 1);

        assertEquals("Expense", ledgerRecord.getCategory());
        assertEquals("Fees", ledgerRecord.getSub1());
        assertEquals("Interest Charged", ledgerRecord.getSub2());
        assertEquals(11265, ledgerRecord.getRuleNo());
    }

    @Test
    void categorizeRecordAssignsSecuritiesSaleCategory() {
        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(java.time.LocalDate.of(2019, 6, 4));
        rec.setDescription("CALL ATVI 07/19/19 47.50 ACTIVISION BLIZZARD INC OPEN CONTRACT");
        rec.setAmount(223.23);

        LedgerRecord ledgerRecord = EtradeBrokerageLoad.categorizeRecord(categorizer, rec, "etrade Brokerage", (short) 1);

        assertEquals("Income", ledgerRecord.getCategory());
        assertEquals("Investment Returns", ledgerRecord.getSub1());
        assertTrue(ledgerRecord.getRuleNo() == 11267);
        assertNotNull(ledgerRecord.getCategory());
    }
}
