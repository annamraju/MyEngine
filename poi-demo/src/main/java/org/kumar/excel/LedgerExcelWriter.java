package org.kumar.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kumar.excel.util.LedgerRecord;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

public class LedgerExcelWriter {

    // Column indexes (0-based)
    private static final int COL_ACCOUNT = 0;
    private static final int COL_ORDER   = 1;
    private static final int COL_YEAR    = 2;
    private static final int COL_MONTH   = 3;
    private static final int COL_DATE    = 4;
    private static final int COL_DESC    = 5;
    private static final int COL_AMOUNT  = 6;
    private static final int COL_BAL     = 7;
    private static final int COL_CAT     = 8;
    private static final int COL_SUB1    = 9;
    private static final int COL_SUB2    = 10;
    private static final int COL_SUB3    = 11;
    private static final int COL_SUB4    = 12;
    private static final int COL_MERCHANT_ID = 13;
    private static final int COL_MERCHANT = 14;
    private static final int COL_RULE_MATCHES = 15;
    private static final int COL_RULE_NO = 16;

    private static final String[] HEADERS = {
            "Account", "Order", "Year", "Month", "Transaction Date", "Description",
            "Amount", "Balance", "Category", "Sub Category 1", "Sub Category2",
            "Sub Category 3", "Sub Category4"
    };

    private static final String[] HEADERS_WITH_CATEGORIZATION = {
            "Account", "Order", "Year", "Month", "Transaction Date", "Description",
            "Amount", "Balance", "Category", "Sub Category 1", "Sub Category2",
            "Sub Category 3", "Sub Category4",
            "MERCHANT_ID", "merchant", "rule matches", "rule no"
    };

    public static void writeLedger(List<LedgerRecord> records, Path outputXlsx) throws Exception {

        // (1) Sort: date (oldest -> latest) then account name
        List<LedgerRecord> sortedRecords = (records == null ? Collections.<LedgerRecord>emptyList() : records)
                .stream()
                .sorted(Comparator
                        .comparing(LedgerRecord::getTransDate, Comparator.nullsLast(Date::compareTo))
                        .thenComparing(LedgerRecord::getAccount, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Runbook");

            // --- Data formats ---
            DataFormat df = wb.createDataFormat();
            short fmtDate = df.getFormat("mm/dd/yyyy");
            short fmtCurrency = df.getFormat("\"$\"#,##0.00_);[Red]\\(\"$\"#,##0.00\\)");

            // --- Fonts ---
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setFontName("Aptos Narrow");

            Font bodyFont = wb.createFont();
            bodyFont.setFontHeightInPoints((short) 10);
            bodyFont.setFontName("Aptos Narrow");

            // --- Styles ---
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setFont(bodyFont);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.cloneStyleFrom(bodyStyle);
            dateStyle.setDataFormat(fmtDate);

            CellStyle currencyStyle = wb.createCellStyle();
            currencyStyle.cloneStyleFrom(bodyStyle);
            currencyStyle.setDataFormat(fmtCurrency);

            // (2) Description: NO WRAP
            CellStyle descNoWrapStyle = wb.createCellStyle();
            descNoWrapStyle.cloneStyleFrom(bodyStyle);
            descNoWrapStyle.setWrapText(false);

            // --- Header row ---
            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c, CellType.STRING);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            // --- Data rows ---
            Locale locale = Locale.US;

            for (int i = 0; i < sortedRecords.size(); i++) {
                LedgerRecord r = sortedRecords.get(i);
                int rowNum = i + 1;
                Row row = sheet.createRow(rowNum);

                // Account
                setString(row, COL_ACCOUNT, r.getAccount(), bodyStyle);

                // Order
                Cell orderCell = row.createCell(COL_ORDER, CellType.NUMERIC);
                orderCell.setCellValue(r.getOrder());
                orderCell.setCellStyle(bodyStyle);

                // Year = YEAR(E[row])
                Cell yearCell = row.createCell(COL_YEAR, CellType.FORMULA);
                yearCell.setCellFormula("YEAR(E" + (rowNum + 1) + ")");
                yearCell.setCellStyle(bodyStyle);

                // Month = TEXT(E[row], "mmmm")
                Cell monthCell = row.createCell(COL_MONTH, CellType.FORMULA);
                monthCell.setCellFormula("TEXT(E" + (rowNum + 1) + ",\"mmmm\")");
                monthCell.setCellStyle(bodyStyle);
                
                // Transaction Date
                Date d = r.getTransDate();
                if (d != null) {
                    Cell dateCell = row.createCell(COL_DATE, CellType.NUMERIC);
                    dateCell.setCellValue(d);
                    dateCell.setCellStyle(dateStyle);
                } else {
                    setString(row, COL_DATE, "", bodyStyle);
                }

                // Description (NO WRAP)
                setString(row, COL_DESC, r.getDescription(), descNoWrapStyle);

                // Amount
                Cell amt = row.createCell(COL_AMOUNT, CellType.NUMERIC);
                amt.setCellValue(r.getAmount());
                amt.setCellStyle(currencyStyle);

                // Balance
                Cell bal = row.createCell(COL_BAL, CellType.NUMERIC);
                bal.setCellValue(r.getBalance());
                bal.setCellStyle(currencyStyle);

                // Category + subs
                setString(row, COL_CAT,  r.getCategory(), bodyStyle);
                setString(row, COL_SUB1, r.getSub1(), bodyStyle);
                setString(row, COL_SUB2, r.getSub2(), bodyStyle);
                setString(row, COL_SUB3, r.getSub3(), bodyStyle);
                setString(row, COL_SUB4, r.getSub4(), bodyStyle);
            }

            // --- Column widths ---
            setColWidthChars(sheet, COL_ACCOUNT, 24.4);
            setColWidthChars(sheet, COL_ORDER,   7.0);
            setColWidthChars(sheet, COL_YEAR,    5.7);
            setColWidthChars(sheet, COL_MONTH,   7.4);
            setColWidthChars(sheet, COL_DATE,    14.7);
            setColWidthChars(sheet, COL_DESC,    47.4);
            setColWidthChars(sheet, COL_AMOUNT,  15.4);
            setColWidthChars(sheet, COL_BAL,     13.4);
            setColWidthChars(sheet, COL_CAT,     9.9);
            setColWidthChars(sheet, COL_SUB1,    17.4);
            setColWidthChars(sheet, COL_SUB2,    14.4);
            setColWidthChars(sheet, COL_SUB3,    16.6);
            setColWidthChars(sheet, COL_SUB4,    12.4);

            // --- Auto filter ---
            int lastRow = Math.max(1, sortedRecords.size());
            sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, COL_SUB4));
            sheet.createFreezePane(0, 1);

            // --- Write file ---
            try (OutputStream os = Files.newOutputStream(outputXlsx)) {
                wb.write(os);
            }
        }
    }

    public static void writeLedgerWithCategorization(List<LedgerRecord> records, Path outputXlsx) throws Exception {
        List<LedgerRecord> sortedRecords = (records == null ? Collections.<LedgerRecord>emptyList() : records)
                .stream()
                .sorted(Comparator
                        .comparing(LedgerRecord::getTransDate, Comparator.nullsLast(Date::compareTo))
                        .thenComparing(LedgerRecord::getAccount, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .collect(Collectors.toList());

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Runbook");

            DataFormat df = wb.createDataFormat();
            short fmtDate = df.getFormat("mm/dd/yyyy");
            short fmtCurrency = df.getFormat("\"$\"#,##0.00_);[Red]\\(\"$\"#,##0.00\\)");

            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setFontName("Aptos Narrow");

            Font bodyFont = wb.createFont();
            bodyFont.setFontHeightInPoints((short) 10);
            bodyFont.setFontName("Aptos Narrow");

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(headerFont);

            CellStyle bodyStyle = wb.createCellStyle();
            bodyStyle.setFont(bodyFont);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.cloneStyleFrom(bodyStyle);
            dateStyle.setDataFormat(fmtDate);

            CellStyle currencyStyle = wb.createCellStyle();
            currencyStyle.cloneStyleFrom(bodyStyle);
            currencyStyle.setDataFormat(fmtCurrency);

            CellStyle descNoWrapStyle = wb.createCellStyle();
            descNoWrapStyle.cloneStyleFrom(bodyStyle);
            descNoWrapStyle.setWrapText(false);

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS_WITH_CATEGORIZATION.length; c++) {
                Cell cell = header.createCell(c, CellType.STRING);
                cell.setCellValue(HEADERS_WITH_CATEGORIZATION[c]);
                cell.setCellStyle(headerStyle);
            }

            for (int i = 0; i < sortedRecords.size(); i++) {
                LedgerRecord r = sortedRecords.get(i);
                int rowNum = i + 1;
                Row row = sheet.createRow(rowNum);

                setString(row, COL_ACCOUNT, r.getAccount(), bodyStyle);

                Cell orderCell = row.createCell(COL_ORDER, CellType.NUMERIC);
                orderCell.setCellValue(r.getOrder());
                orderCell.setCellStyle(bodyStyle);

                Cell yearCell = row.createCell(COL_YEAR, CellType.FORMULA);
                yearCell.setCellFormula("YEAR(E" + (rowNum + 1) + ")");
                yearCell.setCellStyle(bodyStyle);

                Cell monthCell = row.createCell(COL_MONTH, CellType.FORMULA);
                monthCell.setCellFormula("TEXT(E" + (rowNum + 1) + ",\"mmmm\")");
                monthCell.setCellStyle(bodyStyle);

                Date d = r.getTransDate();
                if (d != null) {
                    Cell dateCell = row.createCell(COL_DATE, CellType.NUMERIC);
                    dateCell.setCellValue(d);
                    dateCell.setCellStyle(dateStyle);
                } else {
                    setString(row, COL_DATE, "", bodyStyle);
                }

                setString(row, COL_DESC, r.getDescription(), descNoWrapStyle);

                Cell amt = row.createCell(COL_AMOUNT, CellType.NUMERIC);
                amt.setCellValue(r.getAmount());
                amt.setCellStyle(currencyStyle);

                Cell bal = row.createCell(COL_BAL, CellType.NUMERIC);
                bal.setCellValue(r.getBalance());
                bal.setCellStyle(currencyStyle);

                setString(row, COL_CAT, r.getCategory(), bodyStyle);
                setString(row, COL_SUB1, r.getSub1(), bodyStyle);
                setString(row, COL_SUB2, r.getSub2(), bodyStyle);
                setString(row, COL_SUB3, r.getSub3(), bodyStyle);
                setString(row, COL_SUB4, r.getSub4(), bodyStyle);

                setString(row, COL_MERCHANT_ID, r.getMerchantId(), bodyStyle);
                setString(row, COL_MERCHANT, r.getMerchant(), bodyStyle);
                setBoolean(row, COL_RULE_MATCHES, r.getRuleMatched(), bodyStyle);
                setInteger(row, COL_RULE_NO, r.getRuleNo(), bodyStyle);
            }

            setColWidthChars(sheet, COL_ACCOUNT, 24.4);
            setColWidthChars(sheet, COL_ORDER, 7.0);
            setColWidthChars(sheet, COL_YEAR, 5.7);
            setColWidthChars(sheet, COL_MONTH, 7.4);
            setColWidthChars(sheet, COL_DATE, 14.7);
            setColWidthChars(sheet, COL_DESC, 47.4);
            setColWidthChars(sheet, COL_AMOUNT, 15.4);
            setColWidthChars(sheet, COL_BAL, 13.4);
            setColWidthChars(sheet, COL_CAT, 9.9);
            setColWidthChars(sheet, COL_SUB1, 17.4);
            setColWidthChars(sheet, COL_SUB2, 14.4);
            setColWidthChars(sheet, COL_SUB3, 16.6);
            setColWidthChars(sheet, COL_SUB4, 12.4);
            setColWidthChars(sheet, COL_MERCHANT_ID, 14.0);
            setColWidthChars(sheet, COL_MERCHANT, 18.0);
            setColWidthChars(sheet, COL_RULE_MATCHES, 12.0);
            setColWidthChars(sheet, COL_RULE_NO, 10.0);

            int lastRow = Math.max(1, sortedRecords.size());
            sheet.setAutoFilter(new CellRangeAddress(0, lastRow, 0, COL_RULE_NO));
            sheet.createFreezePane(0, 1);

            try (OutputStream os = Files.newOutputStream(outputXlsx)) {
                wb.write(os);
            }
        }
    }

    private static void setString(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col, CellType.STRING);
        cell.setCellValue(val == null ? "" : val);
        cell.setCellStyle(style);
    }

    private static void setBoolean(Row row, int col, Boolean val, CellStyle style) {
        Cell cell = row.createCell(col, val == null ? CellType.BLANK : CellType.BOOLEAN);
        if (val != null) {
            cell.setCellValue(val.booleanValue());
        }
        cell.setCellStyle(style);
    }

    private static void setInteger(Row row, int col, Integer val, CellStyle style) {
        Cell cell = row.createCell(col, val == null ? CellType.BLANK : CellType.NUMERIC);
        if (val != null) {
            cell.setCellValue(val.intValue());
        }
        cell.setCellStyle(style);
    }

    private static void setColWidthChars(Sheet sheet, int col, double chars) {
        sheet.setColumnWidth(col, (int) Math.round(chars * 256));
    }

    private static String toMonthName(short month, Locale locale) {
        if (month < 1 || month > 12) return "";
        return Month.of(month).getDisplayName(TextStyle.FULL, locale);
    }
}
