package org.kumar.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.time.Month;
import java.util.Date;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.kumar.excel.util.LedgerRecord;

class LedgerExcelWriterTest {

    @Test
    void writeLedgerWithCategorizationWritesExtendedColumns() throws Exception {
        File outputFile = File.createTempFile("ledger-with-categorization-output", ".xlsx");
        outputFile.deleteOnExit();

        LedgerRecord record = new LedgerRecord(
                "Checking", (short) 1, (short) 2026, Month.JANUARY, new Date(0),
                "Publix purchase", -12.34, 100.00,
                "Expense", "Groceries", "Food", null, null);
        record.setMerchantId("PUBLIX");
        record.setMerchant("Publix");
        record.setRuleMatched(true);
        record.setRuleNo(42);

        LedgerExcelWriter.writeLedgerWithCategorization(List.of(record), Path.of(outputFile.getAbsolutePath()));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(outputFile))) {
            XSSFSheet sheet = workbook.getSheet("Runbook");
            XSSFRow header = sheet.getRow(0);
            XSSFRow row = sheet.getRow(1);

            assertEquals("MERCHANT_ID", header.getCell(13).getStringCellValue());
            assertEquals("merchant", header.getCell(14).getStringCellValue());
            assertEquals("rule matches", header.getCell(15).getStringCellValue());
            assertEquals("rule no", header.getCell(16).getStringCellValue());

            assertEquals("PUBLIX", row.getCell(13).getStringCellValue());
            assertEquals("Publix", row.getCell(14).getStringCellValue());
            assertTrue(row.getCell(15).getBooleanCellValue());
            assertEquals(42.0, row.getCell(16).getNumericCellValue());
        }
    }
}
