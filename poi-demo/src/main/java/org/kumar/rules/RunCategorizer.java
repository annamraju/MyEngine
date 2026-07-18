package org.kumar.rules;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kumar.excel.ReadLedgerFile;
import org.kumar.excel.util.LedgerRecord;
import org.kumar.rules.LedgerRecordCategorizer.CategorizationResult;
import org.kumar.rules.LedgerRecordCategorizer.CategoryResult;
import org.kumar.rules.LedgerRecordCategorizer.Engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RunCategorizer {

    private static final int COL_CAT = 8;
    private static final int COL_SUB1 = 9;
    private static final int COL_SUB2 = 10;
    private static final int COL_SUB3 = 11;
    private static final int COL_SUB4 = 12;

    private static final String HEADER_MERCHANT_ID = "MERCHANT_ID";
    private static final String HEADER_MERCHANT = "merchant";
    private static final String HEADER_RULE_MATCHES = "rule matches";
    private static final String HEADER_RULE_NO = "rule no";

    public static void main(String[] args) throws Exception {
        Engine engine = LedgerRecordCategorizer.load(
                "merchant_map.json",
                "rules.json",
                "check_rules.json"
        );

        // you already have this from ReadLedgerFile.readFile(...)
//        String ledgerFile = "D:\\fin_docs\\updated_ledger_01192026_almost_final.xlsx";
        //String ledgerFile = "D:\\fin_docs\\SampleDebug.xlsx";
//        String ledgerFile = "D:\\fin_docs\\Ledger_pre_categorized.xlsx";
        String ledgerFile = "D:\\fin_docs\\Updated_ledger_06212026.xlsx";
        //String ledgerFile = "D:\\fin_docs\\test_trans.xlsx";
        String sheetName  = "Runbook";
  	  	ReadLedgerFile fileReader =  new ReadLedgerFile();
  	  	List<LedgerRecord> ledger = fileReader.readFile(ledgerFile, sheetName);

        List<CategorizationResult> results = categorizeInMemory(engine, ledger);

        int count = 0;
        for(int i = 0; i < results.size(); i++) {
        	CategoryResult x =  (results.get(i)).categoryResult;
        	if(x == null || isBlank(x.r_category)) count++;
        }
        System.out.println(count + " were STILL uncategorized out of " + ledger.size());

        int updatedRows = updateLedgerFile(ledgerFile, sheetName, results);
        System.out.println("Updated categorization output in ledger file: " + Path.of(ledgerFile).toAbsolutePath()
                + " (" + updatedRows + " rows)");

  	  	printStats(results);
  	  	/*
        // Example audit: rule matched but category still blank
        for (CategorizationResult r : results) {
            if (r.ruleMatched && isBlank(r.record.getCategory())) {
                System.out.println("Rule " + r.ruleNoMatched
                        + " matched but category blank. Merchant=" + r.merchant
                        + " Desc=" + r.record.getDescription());
            }
        }
        */
    }

    public static List<CategorizationResult> categorizeInMemory(Engine engine, List<LedgerRecord> ledger) {
        List<CategorizationResult> results = LedgerRecordCategorizer.categorizeAll(engine, ledger);
        for (int i = 0; i < ledger.size(); i++) {
            LedgerRecordCategorizer.applyCategoryResult(ledger.get(i), results.get(i));
        }
        return results;
    }

    public static List<CategorizationResult> categorizeInMemory(
            List<LedgerRecord> ledger,
            String merchantMapResource,
            String rulesResource,
            String checkRulesResource) throws IOException {
        Engine engine = LedgerRecordCategorizer.load(merchantMapResource, rulesResource, checkRulesResource);
        return categorizeInMemory(engine, ledger);
    }

    public static void printStats(List<CategorizationResult> results) {
    	if(results == null || results.size() ==0) return;
    	Map<String, Integer> stats = new HashMap<String, Integer>();
    	for(CategorizationResult res : results) {
    		CategoryResult x =  res.categoryResult;
    		
    		if(x != null && !isBlank(x.r_category)) continue;
    		LedgerRecord rec = res.record;
    		StringBuffer buf =  new StringBuffer();
    		buf.append(isBlank(rec.getCategory()) ? "" : rec.getCategory());
    		buf.append("|");    		
    		buf.append(isBlank(rec.getSub1()) ? "" : rec.getSub1());
    		//buf.append("|");
    		//buf.append(isBlank(rec.getSub2()) ? "" : rec.getSub2());
    		if(stats.containsKey(buf.toString())) {
    			Integer value = stats.get(buf.toString());
    			value =  value + 1;
    			stats.put(buf.toString(), value);
    		}else {
    			stats.put(buf.toString(), 1);
    		}    				
    	}
    	
    	// now print
    	Map<String, Integer> sorted =
    		    stats.entrySet()
    		       .stream()
    		       .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
    		       .collect(Collectors.toMap(
    		           Map.Entry::getKey,
    		           Map.Entry::getValue,
    		           (e1, e2) -> e1,
    		           LinkedHashMap::new
    		       ));

    		System.out.println(sorted);    	
    }

    static int updateLedgerFile(String ledgerFile, String sheetName, List<CategorizationResult> results) throws IOException {
        if (isBlank(ledgerFile)) {
            throw new IllegalArgumentException("ledgerFile cannot be blank");
        }
        if (isBlank(sheetName)) {
            throw new IllegalArgumentException("sheetName cannot be blank");
        }
        if (results == null) {
            throw new IllegalArgumentException("results cannot be null");
        }

        Path ledgerPath = Path.of(ledgerFile);
        XSSFWorkbook workbook;
        try (java.io.InputStream inputStream = Files.newInputStream(ledgerPath)) {
            workbook = new XSSFWorkbook(inputStream);
        }

        int rowsUpdated;
        try (XSSFWorkbook closeableWorkbook = workbook) {
            Sheet sheet = closeableWorkbook.getSheet(sheetName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet not found: " + sheetName);
            }

            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                headerRow = sheet.createRow(sheet.getFirstRowNum());
            }

            Map<String, Integer> headerColumns = getHeaderColumns(headerRow);
            int merchantIdCol = getOrCreateHeaderColumn(headerRow, headerColumns, HEADER_MERCHANT_ID);
            int merchantCol = getOrCreateHeaderColumn(headerRow, headerColumns, HEADER_MERCHANT);
            int ruleMatchesCol = getOrCreateHeaderColumn(headerRow, headerColumns, HEADER_RULE_MATCHES);
            int ruleNoCol = getOrCreateHeaderColumn(headerRow, headerColumns, HEADER_RULE_NO);

            FormulaEvaluator evaluator = closeableWorkbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter();
            int resultIndex = 0;

            for (int rowNum = sheet.getFirstRowNum() + 1; rowNum <= sheet.getLastRowNum(); rowNum++) {
                Row row = sheet.getRow(rowNum);
                if (isBlankLedgerRow(row, formatter, evaluator)) {
                    continue;
                }
                if (resultIndex >= results.size()) {
                    throw new IllegalStateException("More ledger rows than categorization results. First extra spreadsheet row: "
                            + (rowNum + 1));
                }

                CategorizationResult result = results.get(resultIndex);
                writeCategoryResult(row, result.categoryResult);
                setString(row, merchantIdCol, result.merchantID);
                setString(row, merchantCol, result.merchant);
                setBoolean(row, ruleMatchesCol, result.ruleMatched);
                setInteger(row, ruleNoCol, result.ruleNoMatched);
                resultIndex++;
            }

            if (resultIndex != results.size()) {
                throw new IllegalStateException("Categorization produced " + results.size()
                        + " results but only " + resultIndex + " ledger rows were updated");
            }

            rowsUpdated = resultIndex;
            try (java.io.OutputStream outputStream = Files.newOutputStream(ledgerPath)) {
                closeableWorkbook.write(outputStream);
            }
        }

        return rowsUpdated;
    }

    private static Map<String, Integer> getHeaderColumns(Row headerRow) {
        Map<String, Integer> columns = new HashMap<String, Integer>();
        short lastCellNum = headerRow.getLastCellNum();
        if (lastCellNum < 0) {
            return columns;
        }

        for (int cellNum = 0; cellNum < lastCellNum; cellNum++) {
            Cell cell = headerRow.getCell(cellNum, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
            if (cell == null) {
                continue;
            }
            String header = cell.getStringCellValue();
            if (!isBlank(header)) {
                columns.put(normalizeHeader(header), cellNum);
            }
        }
        return columns;
    }

    private static int getOrCreateHeaderColumn(Row headerRow, Map<String, Integer> headerColumns, String headerName) {
        String normalizedHeader = normalizeHeader(headerName);
        Integer existingColumn = headerColumns.get(normalizedHeader);
        if (existingColumn != null) {
            return existingColumn;
        }

        int newColumn = Math.max(headerRow.getLastCellNum(), COL_SUB4 + 1);
        Cell cell = headerRow.createCell(newColumn, CellType.STRING);
        cell.setCellValue(headerName);

        CellStyle headerStyle = findHeaderStyle(headerRow, newColumn);
        if (headerStyle != null) {
            cell.setCellStyle(headerStyle);
        }

        headerColumns.put(normalizedHeader, newColumn);
        return newColumn;
    }

    private static CellStyle findHeaderStyle(Row headerRow, int beforeColumn) {
        for (int cellNum = beforeColumn - 1; cellNum >= 0; cellNum--) {
            Cell cell = headerRow.getCell(cellNum, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
            if (cell != null && cell.getCellStyle() != null) {
                return cell.getCellStyle();
            }
        }
        return null;
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }

    private static void writeCategoryResult(Row row, CategoryResult result) {
        if (result == null) {
            return;
        }
        if (!isBlank(result.r_category)) setString(row, COL_CAT, result.r_category);
        if (!isBlank(result.r_sub1)) setString(row, COL_SUB1, result.r_sub1);
        if (!isBlank(result.r_sub2)) setString(row, COL_SUB2, result.r_sub2);
        if (!isBlank(result.r_sub3)) setString(row, COL_SUB3, result.r_sub3);
        if (!isBlank(result.r_sub4)) setString(row, COL_SUB4, result.r_sub4);
    }

    private static boolean isBlankLedgerRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }
        for (int cellIndex = 0; cellIndex <= COL_SUB4; cellIndex++) {
            Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
            if (cell != null && !isBlank(formatter.formatCellValue(cell, evaluator))) {
                return false;
            }
        }
        return true;
    }

    private static void setString(Row row, int col, String value) {
        Cell cell = getOrCreateCell(row, col, CellType.STRING);
        cell.setCellValue(value == null ? "" : value);
    }

    private static void setBoolean(Row row, int col, boolean value) {
        Cell cell = getOrCreateCell(row, col, CellType.BOOLEAN);
        cell.setCellValue(value);
    }

    private static void setInteger(Row row, int col, Integer value) {
        Cell cell = getOrCreateCell(row, col, value == null ? CellType.BLANK : CellType.NUMERIC);
        if (value == null) {
            cell.setBlank();
        } else {
            cell.setCellValue(value);
        }
    }

    private static Cell getOrCreateCell(Row row, int col, CellType cellType) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
        if (cell == null) {
            cell = row.createCell(col, cellType);
            CellStyle style = findRowStyle(row, col);
            if (style != null) {
                cell.setCellStyle(style);
            }
        } else if (cellType != CellType.BLANK) {
            cell.setCellType(cellType);
        }
        return cell;
    }

    private static CellStyle findRowStyle(Row row, int beforeColumn) {
        for (int cellNum = beforeColumn - 1; cellNum >= 0; cellNum--) {
            Cell cell = row.getCell(cellNum, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
            if (cell != null && cell.getCellStyle() != null) {
                return cell.getCellStyle();
            }
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim());
    }
}
