package org.kumar.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.kumar.excel.util.LedgerRecord;
import org.kumar.rules.LedgerRecordCategorizer.CategorizationResult;
import org.kumar.rules.LedgerRecordCategorizer.CategoryResult;

public class RunCategorizerTest {

    @Test
    void updateLedgerFileWritesCategorizationBackToInputWorkbook() throws Exception {
        File ledgerFile = File.createTempFile("ledger-categorizer-output", ".xlsx");
        ledgerFile.deleteOnExit();

        try (XSSFWorkbook workbook = new XSSFWorkbook();
                FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
            XSSFSheet sheet = workbook.createSheet("Runbook");
            XSSFRow header = sheet.createRow(0);
            String[] headers = {
                    "Account", "Order", "Year", "Month", "Transaction Date", "Description",
                    "Amount", "Balance", "Category", "Sub Category 1", "Sub Category2",
                    "Sub Category 3", "Sub Category4"
            };
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            XSSFRow row = sheet.createRow(1);
            row.createCell(0).setCellValue("Checking");
            row.createCell(1).setCellValue(1);
            row.createCell(2).setCellValue(2026);
            row.createCell(3).setCellValue("June");
            row.createCell(4).setCellValue(new Date(0));
            row.createCell(5).setCellValue("Publix purchase");
            row.createCell(6).setCellValue(-12.34);
            row.createCell(7).setCellValue(100.00);
            row.createCell(8).setCellValue("Old Category");

            sheet.createRow(2);
            workbook.write(outputStream);
        }

        LedgerRecord record = new LedgerRecord("Checking", (short) 1, (short) 2026, "June", new Date(0),
                "Publix purchase", -12.34, 100.00, "Old Category", null, null, null, null);
        CategoryResult categoryResult = new CategoryResult();
        categoryResult.r_category = "Expense";
        categoryResult.r_sub1 = "Groceries";
        categoryResult.r_sub2 = "Food";

        CategorizationResult result = new CategorizationResult(record, "Publix", "PUBLIX", true, 42,
                categoryResult);

        int updatedRows = RunCategorizer.updateLedgerFile(ledgerFile.getAbsolutePath(), "Runbook", List.of(result));

        assertEquals(1, updatedRows);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new FileInputStream(ledgerFile))) {
            XSSFSheet sheet = workbook.getSheet("Runbook");
            XSSFRow header = sheet.getRow(0);
            XSSFRow row = sheet.getRow(1);

            assertEquals("Expense", row.getCell(8).getStringCellValue());
            assertEquals("Groceries", row.getCell(9).getStringCellValue());
            assertEquals("Food", row.getCell(10).getStringCellValue());

            assertEquals("MERCHANT_ID", header.getCell(13).getStringCellValue());
            assertEquals("merchant", header.getCell(14).getStringCellValue());
            assertEquals("rule matches", header.getCell(15).getStringCellValue());
            assertEquals("rule no", header.getCell(16).getStringCellValue());
            assertEquals("PUBLIX", row.getCell(13).getStringCellValue());
            assertEquals("Publix", row.getCell(14).getStringCellValue());
            assertTrue(row.getCell(15).getBooleanCellValue());
            assertEquals(42, (int) row.getCell(16).getNumericCellValue());
        }
    }
}
