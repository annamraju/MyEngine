package org.kumar.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Date;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.kumar.excel.util.LedgerRecord;

public class ReadLedgerFileTest {

	@Test
	void readFileAllowsUncategorizedRowsAndSkipsBlankRows() throws Exception {
		File ledgerFile = File.createTempFile("ledger-with-blank-category", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("Checking");
			row.createCell(1).setCellValue(1);
			row.createCell(2).setCellValue(2026);
			row.createCell(3).setCellValue("January");
			row.createCell(4).setCellValue(new Date(0));
			row.createCell(5).setCellValue("Uncategorized transaction");
			row.createCell(6).setCellValue(-12.34);
			row.createCell(7).setCellValue(100.00);
			// Category and subcategory cells are intentionally absent.

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		LedgerRecord record = records.get(0);
		assertEquals("Checking", record.getAccount());
		assertEquals("Uncategorized transaction", record.getDescription());
		assertEquals(-12.34, record.getAmount());
		assertNull(record.getCategory());
		assertNull(record.getSub1());
	}

	@Test
	void readFile3ReadsCategorizationColumns() throws Exception {
		File ledgerFile = File.createTempFile("ledger-with-categorization-columns", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0).createCell(0).setCellValue("header");

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("Checking");
			row.createCell(1).setCellValue(1);
			row.createCell(2).setCellValue(2026);
			row.createCell(3).setCellValue("January");
			row.createCell(4).setCellValue(new Date(0));
			row.createCell(5).setCellValue("Publix purchase");
			row.createCell(6).setCellValue(-12.34);
			row.createCell(7).setCellValue(100.00);
			row.createCell(8).setCellValue("Expense");
			row.createCell(9).setCellValue("Groceries");
			row.createCell(13).setCellValue("PUBLIX");
			row.createCell(14).setCellValue("Publix");
			row.createCell(15).setCellValue(true);
			row.createCell(16).setCellValue(42);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile3(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		LedgerRecord record = records.get(0);
		assertEquals("Checking", record.getAccount());
		assertEquals("Publix purchase", record.getDescription());
		assertEquals("Expense", record.getCategory());
		assertEquals("Groceries", record.getSub1());
		assertEquals("PUBLIX", record.getMerchantId());
		assertEquals("Publix", record.getMerchant());
		assertTrue(record.getRuleMatched());
		assertEquals(42, record.getRuleNo());
	}
}
