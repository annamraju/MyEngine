package org.kumar.excel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Month;
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
	void readFilePreservesNumericOrderColumn() throws Exception {
		File ledgerFile = File.createTempFile("ledger-numeric-order", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("Checking");
			row.createCell(1).setCellValue(17);
			row.createCell(2).setCellValue(2026);
			row.createCell(3).setCellValue("January");
			row.createCell(4).setCellValue(new Date(0));
			row.createCell(5).setCellValue("Coffee");
			row.createCell(6).setCellValue(-4.50);
			row.createCell(7).setCellValue(100.00);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		assertEquals(17, records.get(0).getOrder());
	}

	@Test
	void readFilePreservesStringOrderColumn() throws Exception {
		File ledgerFile = File.createTempFile("ledger-string-order", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("Checking");
			row.createCell(1).setCellValue("23");
			row.createCell(2).setCellValue(2026);
			row.createCell(3).setCellValue("January");
			row.createCell(4).setCellValue(new Date(0));
			row.createCell(5).setCellValue("Coffee");
			row.createCell(6).setCellValue(-4.50);
			row.createCell(7).setCellValue(100.00);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		assertEquals(23, records.get(0).getOrder());
	}

	@Test
	void readFile2PreservesNumericOrderColumn() throws Exception {
		File ledgerFile = File.createTempFile("ledger-readfile2-order", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("Checking");
			row.createCell(1).setCellValue(9);
			row.createCell(2).setCellFormula("YEAR(E2)");
			row.createCell(3).setCellFormula("TEXT(E2,\"mmmm\")");
			row.createCell(4).setCellValue(java.time.LocalDate.of(2026, 3, 10));
			row.createCell(5).setCellValue("Coffee");
			row.createCell(6).setCellValue(-4.50);
			row.createCell(7).setCellValue(100.00);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile2(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		assertEquals(9, records.get(0).getOrder());
	}

	@Test
	void readFile2PreservesStringOrderColumn() throws Exception {
		File ledgerFile = File.createTempFile("ledger-readfile2-string-order", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("Checking");
			row.createCell(1).setCellValue("15");
			row.createCell(2).setCellFormula("YEAR(E2)");
			row.createCell(3).setCellFormula("TEXT(E2,\"mmmm\")");
			row.createCell(4).setCellValue(java.time.LocalDate.of(2026, 3, 10));
			row.createCell(5).setCellValue("Coffee");
			row.createCell(6).setCellValue(-4.50);
			row.createCell(7).setCellValue(100.00);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile2(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		assertEquals(15, records.get(0).getOrder());
		assertEquals(2026, records.get(0).getYear());
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

	@Test
	void readFile3PreservesStringOrderColumn() throws Exception {
		File ledgerFile = File.createTempFile("ledger-readfile3-order", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("Checking");
			row.createCell(1).setCellValue("7");
			row.createCell(2).setCellFormula("YEAR(E2)");
			row.createCell(3).setCellValue("January");
			row.createCell(4).setCellValue(java.time.LocalDate.of(2024, 1, 15));
			row.createCell(5).setCellValue("Coffee");
			row.createCell(6).setCellValue(-4.50);
			row.createCell(7).setCellValue(100.00);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile3(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		assertEquals(7, records.get(0).getOrder());
		assertEquals(2024, records.get(0).getYear());
	}

	@Test
	void readFileAndReadFile3AgreeOnWriterShapedLedger() throws Exception {
		File ledgerFile = File.createTempFile("ledger-writer-shaped", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("etrade Brokerage");
			row.createCell(1).setCellValue(42);
			row.createCell(2).setCellFormula("YEAR(E2)");
			row.createCell(3).setCellFormula("TEXT(E2,\"mmmm\")");
			row.createCell(4).setCellValue(java.time.LocalDate.of(2026, 6, 15));
			row.createCell(5).setCellValue("Dividend");
			row.createCell(6).setCellValue(25.00);
			row.createCell(7).setCellValue(1000.00);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		ReadLedgerFile reader = new ReadLedgerFile();
		List<LedgerRecord> fromReadFile = reader.readFile(ledgerFile.getAbsolutePath(), "Runbook");
		List<LedgerRecord> fromReadFile3 = reader.readFile3(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, fromReadFile.size());
		assertEquals(1, fromReadFile3.size());
		assertEquals(fromReadFile.get(0).getOrder(), fromReadFile3.get(0).getOrder());
		assertEquals(fromReadFile.get(0).getYear(), fromReadFile3.get(0).getYear());
	}

	@Test
	void readFile3HandlesFormulaMonthColumn() throws Exception {
		File ledgerFile = File.createTempFile("ledger-formula-month", ".xlsx");
		ledgerFile.deleteOnExit();

		try (XSSFWorkbook workbook = new XSSFWorkbook();
				FileOutputStream outputStream = new FileOutputStream(ledgerFile)) {
			XSSFSheet sheet = workbook.createSheet("Runbook");
			sheet.createRow(0);

			XSSFRow row = sheet.createRow(1);
			row.createCell(0).setCellValue("etrade Brokerage");
			row.createCell(1).setCellValue(1);
			row.createCell(2).setCellFormula("YEAR(E2)");
			row.createCell(3).setCellFormula("TEXT(E2,\"mmmm\")");
			row.createCell(4).setCellValue(java.time.LocalDate.of(2026, 6, 15));
			row.createCell(5).setCellValue("Dividend");
			row.createCell(6).setCellValue(25.00);
			row.createCell(7).setCellValue(1000.00);

			sheet.createRow(2);
			workbook.write(outputStream);
		}

		List<LedgerRecord> records = new ReadLedgerFile().readFile3(ledgerFile.getAbsolutePath(), "Runbook");

		assertEquals(1, records.size());
		assertEquals(Month.JUNE, records.get(0).getMonEnum());
	}
}
