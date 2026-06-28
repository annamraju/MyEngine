package org.kumar.dataload.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class EtradeBrokerageRecordTest {

    @Test
    void parseTransactionsFromTextExtractsSecuritiesDividendsAndCashActivity() throws Exception {
        String statementText = String.join("\n",
                "TRANSACTION HISTORY",
                "SECURITIES PURCHASED OR SOLD",
                "TRADE",
                "DATE",
                "05/31/19",
                "11:00",
                "06/04/19 NVIDIA CORP NVDA Bought 20 137.9300 2,762.55",
                "06/04/19",
                "15:56",
                "06/05/19 CALL ATVI   07/19/19    47.50",
                "ACTIVISION BLIZZARD INC",
                "OPEN CONTRACT",
                "Sold -3 0.7600 223.23",
                "TOTAL SECURITIES ACTIVITY $71,830.93 $131,564.03",
                "UNSETTLED TRADES",
                "06/27/19 NEW RELIC INC",
                "COM",
                "NEWR Bought 50 87.3800 4,372.95",
                "DIVIDENDS & INTEREST ACTIVITY",
                "06/03/19 Dividend INTEL CORP",
                "CASH DIV  ON      50 SHS",
                "REC 05/07/19 PAY 06/01/19",
                "INTC 15.75",
                "06/11/19 Dividend ***NOKIA CORPORATION",
                "SPONSORED ADR REPSTG 1 SER A",
                "AGENCY PROCESSING FEE",
                "NOK 2.50",
                "06/11/19 Dividend ***NOKIA CORPORATION",
                "SPONSORED ADR REPSTG 1 SER A",
                "CASH DIV  ON     500 SHS",
                "REC 05/23/19 PAY 06/11/19",
                "FRGN-W/H@SOURCE",
                "NOK 4.21 28.08",
                "06/26/19 Interest FROM 05/26 THRU 06/25 @10 1/2%",
                "BAL   41,092   AVBAL   27,417",
                "247.90",
                "NET DIVIDENDS & INTEREST ACTIVITY $281.15",
                "WITHDRAWALS & DEPOSITS",
                "06/03/19 Adjustment TRNSFR FROM CASH TO MARGIN 3.95",
                "06/03/19 Mark to Mkt MARK TO MARKET 647.71",
                "06/03/19 Mark to Mkt MARK TO MARKET SHORT POS 647.71",
                "06/10/19 Transfer TRANSFER TO XXXXXX7162",
                "REFID:24578471482;",
                "221.00",
                "06/10/19 Credit CUSTOMER PROMOTION",
                "REFID:P6-CSR-1559966879458;",
                "3.95",
                "NET WITHDRAWALS & DEPOSITS $460.91",
                "OTHER ACTIVITY",
                "06/21/19 CALL HD     06/21/19   200",
                "HOME DEPOT INC",
                "OPTION ASSIGNMENT",
                "Assignment 1");

        List<EtradeBrokerageRecord> records = EtradeBrokerageRecord.parseTransactionsFromText(statementText);

        List<EtradeBrokerageRecord> securities = records.stream()
                .filter(record -> "Securities".equals(record.getSection()))
                .collect(Collectors.toList());
        assertEquals(2, securities.size());

        EtradeBrokerageRecord nvda = securities.get(0);
        assertEquals("2019-05-31", nvda.getTransactionDate().toString());
        assertEquals("NVIDIA CORP NVDA", nvda.getDescription());
        assertEquals(-2762.55, nvda.getAmount(), 0.001);

        EtradeBrokerageRecord atvi = securities.get(1);
        assertEquals("2019-06-04", atvi.getTransactionDate().toString());
        assertTrue(atvi.getDescription().contains("CALL ATVI"));
        assertEquals(223.23, atvi.getAmount(), 0.001);

        List<EtradeBrokerageRecord> unsettled = records.stream()
                .filter(record -> "Unsettled Trades".equals(record.getSection()))
                .collect(Collectors.toList());
        assertEquals(1, unsettled.size());
        assertEquals(-4372.95, unsettled.get(0).getAmount(), 0.001);

        List<EtradeBrokerageRecord> dividends = records.stream()
                .filter(record -> "Dividends & Interest".equals(record.getSection()))
                .collect(Collectors.toList());
        assertEquals(4, dividends.size());
        assertEquals(15.75, dividends.get(0).getAmount(), 0.001);
        assertEquals(2.50, dividends.get(1).getAmount(), 0.001);
        assertEquals(23.87, dividends.get(2).getAmount(), 0.001);
        assertEquals(-247.90, dividends.get(3).getAmount(), 0.001);

        List<EtradeBrokerageRecord> cash = records.stream()
                .filter(record -> "Withdrawals & Deposits".equals(record.getSection()))
                .collect(Collectors.toList());
        assertEquals(2, cash.size());
        assertTrue(cash.stream().noneMatch(record -> record.getDescription().contains("MARGIN")));
        assertTrue(cash.stream().noneMatch(record -> record.getDescription().contains("MARK TO MARKET")));

        EtradeBrokerageRecord transfer = cash.stream()
                .filter(record -> record.getDescription().contains("TRANSFER TO XXXXXX7162"))
                .findFirst()
                .orElseThrow();
        assertEquals(-221.00, transfer.getAmount(), 0.001);

        EtradeBrokerageRecord promotion = cash.stream()
                .filter(record -> record.getDescription().contains("CUSTOMER PROMOTION"))
                .findFirst()
                .orElseThrow();
        assertEquals(3.95, promotion.getAmount(), 0.001);

        assertTrue(records.stream().noneMatch(record -> "Other Activity".equals(record.getSection())));
    }

    @Test
    void isInternalHousekeepingIdentifiesNonCashRows() {
        assertTrue(EtradeBrokerageRecord.isInternalHousekeeping(
                "Adjustment", "Adjustment - TRNSFR FROM CASH TO MARGIN"));
        assertTrue(EtradeBrokerageRecord.isInternalHousekeeping(
                "Adjustment", "Adjustment - TRNSFR FROM MARGIN TO CASH"));
        assertTrue(EtradeBrokerageRecord.isInternalHousekeeping(
                "Mark to Mkt", "Mark to Mkt - MARK TO MARKET"));
        assertTrue(EtradeBrokerageRecord.isInternalHousekeeping(
                "Mark to Mkt", "Mark to Mkt - MARK TO MARKET SHORT POS"));

        assertFalse(EtradeBrokerageRecord.isInternalHousekeeping(
                "Transfer", "Transfer - TRANSFER TO XXXXXX7162"));
        assertFalse(EtradeBrokerageRecord.isInternalHousekeeping(
                "Credit", "Credit - CUSTOMER PROMOTION REFID:P6-CSR-1559966879458;"));
    }
}
