package org.kumar.dataload.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class MarcusSavingsRecordTest {

    @Test
    void parseTransactionsFromTextDoesNotAppendPageMarketingToLastTransaction() throws Exception {
        String statementText = String.join("\n",
                "Statement Period",
                "11/01/2025 to 11/30/2025",
                "Page 1 of 3",
                "ACCOUNT ACTIVITY",
                "Date \tDescription \tCredits \tDebits \tBalance",
                "11/01/2025 \tBeginning Balance \t$230,308.03",
                "11/03/2025 \tACH Deposit Internet transfer from G T E FEDERAL CREDIT",
                "UNION DDA account ****************5076",
                "$3,600.00 \t$233,908.03",
                "11/05/2025 \tACH Withdrawal Internet transfer to BANK OF AMERICA, N.A.",
                "DDA account ****************5186",
                "$6,000.00 \t$227,908.03",
                "11/07/2025 \tACH Deposit Internet transfer from G T E FEDERAL CREDIT",
                "UNION DDA account ****************5076",
                "$1,500.00 \t$229,408.03",
                "11/17/2025 \tACH Deposit Internet transfer from G T E FEDERAL CREDIT",
                "UNION DDA account ****************5076",
                "$3,750.00 \t$233,158.03",
                "11/20/2025 \tACH Withdrawal Internet transfer to BANK OF AMERICA, N.A.",
                "DDA account ****************5186",
                "$4,000.00 \t$229,158.03",
                "11/21/2025 \tACH Withdrawal JPMorgan Chase Ext Trnsfr \t$6,000.00 \t$223,158.03",
                "11/21/2025 \tACH Deposit COINBASE INC. 9682576C \t$65,591.29 \t$288,749.32",
                "Spend smart during the holidays",
                "Check out our spending and saving tips to help avoid some of the financial stressors that come with",
                "the holiday season. Learn more at marcus.com/holidayspending.",
                "-- 1 of 3 --",
                "Statement Period",
                "11/01/2025 to 11/30/2025",
                "Page 2 of 3",
                "ACCOUNT ACTIVITY (continued)",
                "Date \tDescription \tCredits \tDebits \tBalance",
                "11/24/2025 \tACH Deposit Internet transfer from G T E FEDERAL CREDIT",
                "UNION DDA account ****************5076",
                "$1,500.00 \t$290,249.32",
                "11/30/2025 \tInterest Paid \t$738.14 \t$290,987.46",
                "11/30/2025 \tEnding Balance \t$290,987.46",
                "-- 2 of 3 --",
                "In Case of Errors or Questions About Your Electronic Transfers:");

        List<MarcusSavingsRecord> records = MarcusSavingsRecord.parseTransactionsFromText(statementText);

        assertEquals(9, records.size());

        List<MarcusSavingsRecord> coinbaseRecords = records.stream()
                .filter(record -> record.getDescription().contains("COINBASE"))
                .collect(Collectors.toList());
        assertEquals(1, coinbaseRecords.size());

        MarcusSavingsRecord coinbaseRecord = coinbaseRecords.get(0);
        assertEquals("ACH Deposit COINBASE INC. 9682576C", coinbaseRecord.getDescription());
        assertFalse(coinbaseRecord.getDescription().contains("Spend smart"));
        assertEquals(65591.29, coinbaseRecord.getAmount(), 0.001);
        assertEquals(288749.32, coinbaseRecord.getBalance(), 0.001);
    }
}
