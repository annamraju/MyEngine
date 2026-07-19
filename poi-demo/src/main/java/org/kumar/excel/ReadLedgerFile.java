package org.kumar.excel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.TextStyle;
import java.util.Locale;

import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.*;
import org.kumar.excel.util.LedgerRecord;
import org.apache.poi.ss.usermodel.Row;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class ReadLedgerFile {

	private static final Logger LOGGER = LogManager.getLogger(ReadLedgerFile.class);
	private static final String myDelimiter = ":";
	private static final Row.MissingCellPolicy CELL_POLICY = Row.MissingCellPolicy.RETURN_NULL_AND_BLANK;
	private static final int COL_ACCOUNT = 0;
	private static final int COL_ORDER = 1;
	private static final int COL_YEAR = 2;
	private static final int COL_MONTH = 3;
	private static final int COL_DATE = 4;
	private static final int COL_DESC = 5;
	private static final int COL_AMOUNT = 6;
	private static final int COL_BALANCE = 7;
	private static final int COL_CAT = 8;
	private static final int COL_SUB1 = 9;
	private static final int COL_SUB2 = 10;
	private static final int COL_SUB3 = 11;
	private static final int COL_SUB4 = 12;
	private static final int COL_MERCHANT_ID = 13;
	private static final int COL_MERCHANT = 14;
	private static final int COL_RULE_MATCHES = 15;
	private static final int COL_RULE_NO = 16;
	
	public static final Logger getLogger() {
		return LOGGER;
	}

	/**
	 * 
	 * @param cell
	 * @param evaluator
	 * @return
	 */
	public int getFormulaResultNumeric(XSSFCell cell, FormulaEvaluator evaluator) {
		if (cell == null) return 0;

	    if (cell.getCellType() == CellType.FORMULA) {
	        CellValue cv = evaluator.evaluate(cell);
	        if (cv.getCellType() == CellType.NUMERIC) {
	            return (int) cv.getNumberValue();
	        }
	        if (cv.getCellType() == CellType.STRING) {
	            return parseIntegerText(cv.getStringValue());
	        }
	        return 0;
	    }

	    if (cell.getCellType() == CellType.NUMERIC) {
	        return (int)(cell.getNumericCellValue());
	    }

	    if (cell.getCellType() == CellType.STRING) {
	        return parseIntegerText(cell.getStringCellValue());
	    }

	    return 0;
	}
	
	/**
	 * 
	 * @param cell
	 * @return
	 */
	public int getFormulaResultNumeric(XSSFCell cell) {
		if (cell == null) {
			return 0;
		}
		if (cell.getCellType() == CellType.FORMULA) {
			switch (cell.getCachedFormulaResultType()) {
			case NUMERIC:
				return (int) cell.getNumericCellValue();
			case STRING:
				return parseIntegerText(cell.getStringCellValue());
			default:
				return 0;
			}
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			return (int) cell.getNumericCellValue();
		}
		if (cell.getCellType() == CellType.STRING) {
			return parseIntegerText(cell.getStringCellValue());
		}
		return 0;
	}

	private static int parseIntegerText(String value) {
		if (value == null || value.isBlank()) {
			return 0;
		}
		try {
			return (int) Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Reads the Order column the same way as {@link #readFile(String, String)}:
	 * numeric cells, string cells ("17"), and formulas that evaluate to either.
	 */
	private int readOrderColumn(XSSFRow row, FormulaEvaluator evaluator) {
		if (row == null) {
			return 0;
		}
		return getFormulaResultNumeric(row.getCell(COL_ORDER, CELL_POLICY), evaluator);
	}

	/**
	 * Reads the Year column from formula or numeric cells.
	 */
	private int readYearColumn(XSSFRow row, FormulaEvaluator evaluator) {
		if (row == null) {
			return 0;
		}
		return getFormulaResultNumeric(row.getCell(COL_YEAR, CELL_POLICY), evaluator);
	}

	/**
	 * readFile2/readFile3 expect Year as a formula derived from the transaction date.
	 */
	private int readYearFormulaColumn(XSSFRow row, FormulaEvaluator evaluator) {
		if (row == null) {
			return 0;
		}
		XSSFCell yearCell = row.getCell(COL_YEAR, CELL_POLICY);
		if (yearCell != null && yearCell.getCellType() == CellType.FORMULA) {
			return getFormulaResultNumeric(yearCell, evaluator);
		}
		return 0;
	}

	/**
	 * readFile3 also accepts a literal numeric Year when the formula was flattened.
	 */
	private int readYearFormulaOrNumericColumn(XSSFRow row, FormulaEvaluator evaluator) {
		if (row == null) {
			return 0;
		}
		XSSFCell yearCell = row.getCell(COL_YEAR, CELL_POLICY);
		if (yearCell == null) {
			return 0;
		}
		if (yearCell.getCellType() == CellType.FORMULA) {
			return getFormulaResultNumeric(yearCell, evaluator);
		}
		if (yearCell.getCellType() == CellType.NUMERIC) {
			return (int) yearCell.getNumericCellValue();
		}
		return 0;
	}

	/**
	 * 
	 * @param cell
	 * @param evaluator
	 * @return
	 */
	public String getFormulaResultText(XSSFCell cell, FormulaEvaluator evaluator) {
		if (cell == null) return "";

	    if (cell.getCellType() == CellType.FORMULA) {
	        CellValue cv = evaluator.evaluate(cell);
	        return cv.getCellType() == CellType.STRING
	                ? cv.getStringValue().trim()
	                : "";
	    }

	    if (cell.getCellType() == CellType.STRING) {
	        return cell.getStringCellValue().trim();
	    }

	    return "";
	}	
	
	/**
	 * return formula as a text
	 * @param cell
	 * @return
	 */
	public String getFormulaResultText(XSSFCell cell) {
		String result = "";
		if(cell.getCellType()== CellType.FORMULA) {
			switch (cell.getCachedFormulaResultType()) {
	        case STRING :
	            result =  cell.getStringCellValue();
	            break;
	        default:
	            System.out.println("default " + cell.getNumericCellValue());
	            break;
			}
		}
		return result;
	}	

	private String getCellText(XSSFRow row, int cellIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
		if(row == null) return null;
		XSSFCell cell = row.getCell(cellIndex, CELL_POLICY);
		if(cell == null) return null;

		String value = formatter.formatCellValue(cell, evaluator).trim();
		return value.length() == 0 ? null : value;
	}

	private String getRequiredCellText(XSSFRow row, int cellIndex, String fieldName, int rowNum,
			DataFormatter formatter, FormulaEvaluator evaluator) {
		String value = getCellText(row, cellIndex, formatter, evaluator);
		if(value == null) {
			throw new IllegalArgumentException("Missing " + fieldName + " at spreadsheet row " + (rowNum + 1));
		}
		return value;
	}

	private Date getRequiredDate(XSSFRow row, int cellIndex, String fieldName, int rowNum) {
		if(row == null) {
			throw new IllegalArgumentException("Missing " + fieldName + " at spreadsheet row " + (rowNum + 1));
		}
		XSSFCell cell = row.getCell(cellIndex, CELL_POLICY);
		if(cell == null) {
			throw new IllegalArgumentException("Missing " + fieldName + " at spreadsheet row " + (rowNum + 1));
		}
		return cell.getDateCellValue();
	}

	private double getRequiredNumeric(XSSFRow row, int cellIndex, String fieldName, int rowNum) {
		if(row == null) {
			throw new IllegalArgumentException("Missing " + fieldName + " at spreadsheet row " + (rowNum + 1));
		}
		XSSFCell cell = row.getCell(cellIndex, CELL_POLICY);
		if(cell == null) {
			throw new IllegalArgumentException("Missing " + fieldName + " at spreadsheet row " + (rowNum + 1));
		}
		return cell.getNumericCellValue();
	}

	private boolean isBlankLedgerRow(XSSFRow row, DataFormatter formatter, FormulaEvaluator evaluator) {
		if(row == null) return true;

		for(int cellIndex = 0; cellIndex <= 12; cellIndex++) {
			if(getCellText(row, cellIndex, formatter, evaluator) != null) {
				return false;
			}
		}
		return true;
	}

	private String getOptionalStringCell(XSSFRow row, int cellIndex) {
		if (row == null) {
			return null;
		}
		XSSFCell cell = row.getCell(cellIndex, CELL_POLICY);
		if (cell == null) {
			return null;
		}
		if (cell.getCellType() == CellType.STRING) {
			String value = cell.getStringCellValue().trim();
			return value.isEmpty() ? null : value;
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			return String.valueOf(cell.getNumericCellValue());
		}
		if (cell.getCellType() == CellType.BOOLEAN) {
			return String.valueOf(cell.getBooleanCellValue());
		}
		return null;
	}

	private Boolean getOptionalBooleanCell(XSSFRow row, int cellIndex) {
		if (row == null) {
			return null;
		}
		XSSFCell cell = row.getCell(cellIndex, CELL_POLICY);
		if (cell == null || cell.getCellType() == CellType.BLANK) {
			return null;
		}
		if (cell.getCellType() == CellType.BOOLEAN) {
			return cell.getBooleanCellValue();
		}
		if (cell.getCellType() == CellType.STRING) {
			String value = cell.getStringCellValue().trim();
			if (value.isEmpty()) {
				return null;
			}
			return Boolean.parseBoolean(value);
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			return cell.getNumericCellValue() != 0.0d;
		}
		return null;
	}

	private Integer getOptionalIntegerCell(XSSFRow row, int cellIndex) {
		if (row == null) {
			return null;
		}
		XSSFCell cell = row.getCell(cellIndex, CELL_POLICY);
		if (cell == null || cell.getCellType() == CellType.BLANK) {
			return null;
		}
		if (cell.getCellType() == CellType.NUMERIC) {
			return (int) cell.getNumericCellValue();
		}
		if (cell.getCellType() == CellType.FORMULA) {
			return getFormulaResultNumeric(cell);
		}
		if (cell.getCellType() == CellType.STRING) {
			String value = cell.getStringCellValue().trim();
			if (value.isEmpty()) {
				return null;
			}
			return Integer.parseInt(value);
		}
		return null;
	}

	/**
	 * this one expects Month as a number
	 * @param inFile
	 * @param sheetName
	 * @return
	 * @throws FileNotFoundException
	 */
	public List<LedgerRecord> readFile(String inFile, String sheetName) throws FileNotFoundException{
		List<LedgerRecord> records =  new ArrayList<LedgerRecord>();
		
		File fileHandle =  null;
		FileInputStream reader = null; 
		
		try{
			fileHandle =  new File(inFile);
			reader = new FileInputStream (fileHandle);
			XSSFWorkbook workbook = new XSSFWorkbook(reader);
			XSSFSheet mainSheet = workbook.getSheet(sheetName);
			FormulaEvaluator evaluator =
				    workbook.getCreationHelper().createFormulaEvaluator();
			DataFormatter dataFormatter = new DataFormatter();

			System.out.println("lastrow num " + mainSheet.getLastRowNum());
			for(int rowNum = mainSheet.getFirstRowNum()+1; rowNum <= mainSheet.getLastRowNum(); rowNum++) { 
				if(rowNum % 100 == 0)
					System.out.println("reading " + rowNum);
				
				XSSFRow row = mainSheet.getRow(rowNum);
				if(isBlankLedgerRow(row, dataFormatter, evaluator)) {
					continue;
				}
				// now read the columns
				// columns in the order are
				// account type, order, year(formula), month(formula), transDate, amount, balance,
				// category, sub category 1, sub category 2, sub category 3, sub category 4
				
				String accountType = getRequiredCellText(row, 0, "account type", rowNum, dataFormatter, evaluator);
				//System.out.println(" got " + accountType);
				
				int order = readOrderColumn(row, evaluator);
				int year = readYearColumn(row, evaluator);
				
				/*
				int month = 0;
				XSSFCell cell4 =  row.getCell(3,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				if(cell4.getCellType()== CellType.FORMULA) {
					month = getFormulaResultNumeric(cell4);
				}
				*/
				
				String monthStr = getRequiredCellText(row, 3, "month", rowNum, dataFormatter, evaluator);
				//LOGGER.debug(" got monthstr as " + monthStr + " for row " + rowNum);
				
				Date transactionDate = getRequiredDate(row, 4, "transaction date", rowNum);

				String description = getRequiredCellText(row, 5, "description", rowNum, dataFormatter, evaluator);
				
				double amount = getRequiredNumeric(row, 6, "amount", rowNum);
				XSSFCell cell6 = row.getCell(7, CELL_POLICY);
				double balance = 0.0;
				if(cell6 != null && cell6.getCellType() != CellType.FORMULA) {
					balance = cell6.getNumericCellValue();
				}
				
				String category = getCellText(row, 8, dataFormatter, evaluator);
				
				String subCategory1 = getCellText(row, 9, dataFormatter, evaluator);
				String subCategory2 = getCellText(row, 10, dataFormatter, evaluator);
				String subCategory3 = getCellText(row, 11, dataFormatter, evaluator);
				String subCategory4 = getCellText(row, 12, dataFormatter, evaluator);

				/*
				LedgerRecord rec = new LedgerRecord(accountType, (short)order, (short)year, (short)month, transactionDate, 
													description, amount, balance, category, 
													subCategory1, subCategory2, subCategory3, subCategory4);
				*/
				LedgerRecord rec = new LedgerRecord(accountType, (short)order, (short)year, monthStr, transactionDate, 
						description, amount, balance, category, 
						subCategory1, subCategory2, subCategory3, subCategory4);
				records.add(rec);
			}
		}catch(IllegalArgumentException e2) {
			e2.printStackTrace();
			LOGGER.error("some issue in reading the file = aborting");
			throw e2;
		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			try {
				if(reader != null ) reader.close();
			}catch(Exception e2) {
			}
		}
		
		return records;
	}
	
	/**
	 * This one expects the month as a text value
	 * @param inFile
	 * @param sheetName
	 * @return
	 * @throws FileNotFoundException
	 */
	public List<LedgerRecord> readFile2(String inFile, String sheetName) throws FileNotFoundException{
		List<LedgerRecord> records =  new ArrayList<LedgerRecord>();
		
		File fileHandle =  null;
		FileInputStream reader = null; 
		XSSFWorkbook workbook = null;
		
		try{
			fileHandle =  new File(inFile);
			reader = new FileInputStream (fileHandle);
			workbook = new XSSFWorkbook(reader);
			XSSFSheet mainSheet = workbook.getSheet(sheetName);
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

			System.out.println("lastrow num " + mainSheet.getLastRowNum());
			for(int rowNum = mainSheet.getFirstRowNum()+1; rowNum <= mainSheet.getLastRowNum(); rowNum++) { 
				//System.out.println("reading " + rowNum);
				XSSFRow row = mainSheet.getRow(rowNum);
				if (row == null) {
					continue;
				}
				// now read the columns
				// columns in the order are
				// account type, order, year(formula), month(formula), transDate, amount, balance,
				// category, sub category 1, sub category 2, sub category 3, sub category 4
				String accountType =  (row.getCell(0, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)).getStringCellValue().trim();
				//System.out.println(" got " + accountType);
				int order = readOrderColumn(row, evaluator);
				int year = readYearFormulaColumn(row, evaluator);
				String month = "";
				XSSFCell cell4 =  row.getCell(3,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				if(cell4 != null && cell4.getCellType()== CellType.FORMULA) {
					month = getFormulaResultText(cell4, evaluator);
				}
				
				XSSFCell cell5 =  row.getCell(4,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				Date transactionDate =  cell5.getDateCellValue();

				String description = (row.getCell(5, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)).getStringCellValue().trim();
				
				double amount =  (row.getCell(6, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)).getNumericCellValue();
				XSSFCell cell6 = row.getCell(7, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
				double balance = 0.0;
				if(cell6 != null && cell6.getCellType() != CellType.FORMULA) {
					balance = cell6.getNumericCellValue();
				}
				
				String category = null;
				XSSFCell cell7 = row.getCell(8, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
				if(cell7 != null) {
					category =  cell7.getStringCellValue().trim();
				}
				
				String subCategory1 = null;
				XSSFCell cell8 = row.getCell(9, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
				if(cell8 != null) {
					subCategory1 = cell8.getStringCellValue().trim();
				}
				String subCategory2 = null;
				XSSFCell cell9 = row.getCell(10, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
				if(cell9 != null) {
					subCategory2 = cell9.getStringCellValue().trim();
				}
				String subCategory3 = null;
				XSSFCell cell10 = row.getCell(11, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
				if(cell10 != null) {
					subCategory3 = cell10.getStringCellValue().trim();
				}
				String subCategory4 = null;
				XSSFCell cell11 = row.getCell(12, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
				if(cell11 != null) {
					subCategory4 = cell11.getStringCellValue().trim();
				}
				
				LedgerRecord rec = new LedgerRecord(accountType, (short)order, (short)year, month, transactionDate, 
													description, amount, balance, category, 
													subCategory1, subCategory2, subCategory3, subCategory4);
				records.add(rec);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			try {
				if(reader != null ) reader.close();
			}catch(Exception e2) {
			}
		}
		
		return records;
	}

	/**
	 * Like {@link #readFile2(String, String)} but also reads categorization metadata columns:
	 * MERCHANT_ID, merchant, rule matches, and rule no.
	 */
	public List<LedgerRecord> readFile3(String inFile, String sheetName) throws FileNotFoundException {
		List<LedgerRecord> records = new ArrayList<LedgerRecord>();

		File fileHandle = null;
		FileInputStream reader = null;

		try {
			fileHandle = new File(inFile);
			reader = new FileInputStream(fileHandle);
			XSSFWorkbook workbook = new XSSFWorkbook(reader);
			XSSFSheet mainSheet = workbook.getSheet(sheetName);
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			DataFormatter dataFormatter = new DataFormatter();

			System.out.println("lastrow num " + mainSheet.getLastRowNum());
			for (int rowNum = mainSheet.getFirstRowNum() + 1; rowNum <= mainSheet.getLastRowNum(); rowNum++) {
				try {
					XSSFRow row = mainSheet.getRow(rowNum);
					if (row == null || isBlankLedgerRow(row, dataFormatter, evaluator)) {
						continue;
					}

					String accountType = getRequiredCellText(row, 0, "account type", rowNum, dataFormatter, evaluator);

					int order = readOrderColumn(row, evaluator);
					int year = readYearFormulaOrNumericColumn(row, evaluator);

					Date transactionDate = getRequiredDate(row, 4, "transaction date", rowNum);
					String month = readMonthValue(row, evaluator, dataFormatter, transactionDate);

					String description = getRequiredCellText(row, 5, "description", rowNum, dataFormatter, evaluator);
					double amount = getRequiredNumeric(row, 6, "amount", rowNum);

					XSSFCell cell6 = row.getCell(7, CELL_POLICY);
					double balance = 0.0;
					if (cell6 != null && cell6.getCellType() != CellType.FORMULA) {
						balance = cell6.getNumericCellValue();
					}

					String category = getCellText(row, 8, dataFormatter, evaluator);
					String subCategory1 = getCellText(row, 9, dataFormatter, evaluator);
					String subCategory2 = getCellText(row, 10, dataFormatter, evaluator);
					String subCategory3 = getCellText(row, 11, dataFormatter, evaluator);
					String subCategory4 = getCellText(row, 12, dataFormatter, evaluator);

					LedgerRecord rec = new LedgerRecord(accountType, (short) order, (short) year, month, transactionDate,
							description, amount, balance, category,
							subCategory1, subCategory2, subCategory3, subCategory4);
					rec.setMerchantId(getOptionalStringCell(row, COL_MERCHANT_ID));
					rec.setMerchant(getOptionalStringCell(row, COL_MERCHANT));
					rec.setRuleMatched(getOptionalBooleanCell(row, COL_RULE_MATCHES));
					rec.setRuleNo(getOptionalIntegerCell(row, COL_RULE_NO));
					records.add(rec);
				} catch (Exception rowError) {
					LOGGER.error("Failed reading ledger row {} in {}", rowNum + 1, inFile, rowError);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed reading ledger file {}", inFile, e);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (Exception e2) {
			}
		}

		return records;
	}

	private String readMonthValue(XSSFRow row, FormulaEvaluator evaluator, DataFormatter dataFormatter, Date transactionDate) {
		XSSFCell monthCell = row.getCell(3, CELL_POLICY);
		if (monthCell == null) {
			return LedgerRecord.parseMonth(null, transactionDate).getDisplayName(TextStyle.FULL, Locale.US);
		}

		String month = getFormulaResultText(monthCell, evaluator);
		if (month == null || month.isBlank()) {
			month = dataFormatter.formatCellValue(monthCell, evaluator).trim();
		}
		if (month == null || month.isBlank()) {
			return LedgerRecord.parseMonth(null, transactionDate).getDisplayName(TextStyle.FULL, Locale.US);
		}
		return month;
	}

	//prepare 
	public Map<String, Map<String, Integer>> prepareMap(List<LedgerRecord> records){
		Map<String, Map<String, Integer>> resultMap = null;
		
		if(records.isEmpty()) return resultMap;
		
		resultMap = new HashMap<String, Map<String, Integer>>();
		Iterator<LedgerRecord> iter = records.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec = (LedgerRecord)iter.next();

			if(rec.getCategory() == null || ((rec.getCategory()).trim()).length() <= 0)
				continue;
			
			// prepare the key is a combination of (description), account  and (negative or positive) 
			StringBuffer keyBuffer = new StringBuffer();
			keyBuffer.append(rec.getDescription().trim());
			keyBuffer.append("|");
			keyBuffer.append(rec.getAccount());
			keyBuffer.append("|");
			
			if(rec.getAmount() >= 0)
				keyBuffer.append("Positive");
			else
				keyBuffer.append("Negative");
			
			String key = keyBuffer.toString();

			// and the value (category|sub1|sub2|sub3|sub4) 
			StringBuffer buf = new StringBuffer();
			buf.append(rec.getCategory().trim());
			if(rec.getSub1() != null && ((rec.getSub1()).trim()).length() > 0) {
				buf.append(myDelimiter);
				buf.append(rec.getSub1().trim());
				if(rec.getSub2() != null && ((rec.getSub2()).trim()).length() > 0) {
					buf.append(myDelimiter);
					buf.append(rec.getSub2().trim());
					if(rec.getSub3() != null && ((rec.getSub3()).trim()).length() > 0) {
						buf.append(myDelimiter);
						buf.append(rec.getSub3().trim());
						if(rec.getSub4() != null && ((rec.getSub4()).trim()).length() > 0) {
							buf.append(myDelimiter);
							buf.append(rec.getSub4().trim());
						}
					}
				}
			}
			String value = buf.toString();
			
			//check for existence in the map
			if(resultMap.containsKey(key)) {
				//LOGGER.debug("this key " + key + "exists");
				Map<String, Integer> existingValue = resultMap.get(key);
				if(existingValue.containsKey(value)) {
					//LOGGER.debug("this value " + value + " exists inside the key");
					Integer counter = existingValue.get(value);
					counter += 1;
					existingValue.put(value, counter);
					if(counter.intValue() > 1 ) {
						//LOGGER.debug(" more than " + counter.intValue() + " instance for " + value);
					}
					resultMap.put(key, existingValue);
				}else {
					//this is where if we can find out if there is a key out there which is an extension to this
					//get a list of all keys that exist
					Set<String> keys = existingValue.keySet();
					Iterator<String> iter1 = keys.iterator();
					String extension = null;
					boolean foundExtension =  false;
					while(iter1.hasNext()) {
						String s = iter1.next();
						// check if this string and our value are extensions
						extension  = compareAndReturnExtension(value, s);
						if(extension != null) {
							foundExtension = true;
							// remove this entry for s
							if(s.equalsIgnoreCase(extension)) {
								// increment the value
								Integer counter = existingValue.get(s);
								counter += 1;
								existingValue.put(s, counter);
								resultMap.put(key, existingValue);								
							}else {
								// value is the extension
								//replace the existing key with this new extension
								Integer counter = existingValue.get(s);
								counter += 1;
								existingValue.remove(s);
								existingValue.put(value, counter);
								resultMap.put(key, existingValue);								
							}
							break;
						}
					} // end of while
					if(!foundExtension) {
						Integer counter = 1;
						existingValue.put(value, counter);						
						resultMap.put(key, existingValue);								
					}
				}// end of else
			}else {
				//resultMap does not contain the key
				Integer counter = 1;
				Map<String, Integer> newValue =  new HashMap<String, Integer>();
				newValue.put(value, counter);
				resultMap.put(key, newValue);	
			}
		}// end of iter while
		return resultMap;
	}
	
	public String compareAndReturnExtension(String value1, String value2) {
		if(value1 == null || value2 == null) return null;
		
		//LOGGER.info("inside compareandreturn -  " + value1 + " and " + value2);
		String[] value1Set = value1.split(myDelimiter);
		String[] value2Set = value2.split(myDelimiter);
		
		//LOGGER.info("inside compareandreturn -  " + value1Set.length + " and " + value2Set.length);
		
		
		if(value1Set.length == 0 || value2Set.length == 0 ) return null;
		
		// check if categories are the same
		if(value1Set[0].equalsIgnoreCase(value2Set[0])) {
			if(value1Set.length == 1 && value2Set.length == 1) return null; // this is not possible			
			if(value1Set.length == 1 && value2Set.length > 1) return value2;			
			if(value1Set.length > 1 && value2Set.length == 1) return value1;	
			if(value1Set[1].equalsIgnoreCase(value2Set[1])) {
				//check the next level of values
				if(value1Set.length == 2 && value2Set.length == 2) return null; // this is not possible			
				if(value1Set.length == 2 && value2Set.length > 2 ) return value2;			
				if(value1Set.length > 2 && value2Set.length == 2) return value1;	
				if(value1Set[2].equalsIgnoreCase(value2Set[2])) {
					//check the next level of values
					if(value1Set.length == 3 && value2Set.length == 3) return null; // this is not possible			
					if(value1Set.length == 3 && value2Set.length > 3) return value2;			
					if(value1Set.length > 3 && value2Set.length == 3) return value1;	
					if(value1Set[3].equalsIgnoreCase(value2Set[3])) {
						//check the next level of values
						if(value1Set.length == 4 && value2Set.length == 4) return null; // this is not possible			
						if(value1Set.length == 4 && value2Set.length > 4) return value2;			
						if(value1Set.length > 4 && value2Set.length == 4) return value1;	
						if(value1Set[4].equalsIgnoreCase(value2Set[4])) {
							//no more values to check
							// this is not possible
							return null;
						}else {
							// these are truly different values
							return null;													
						}
					}else {
						// these are truly different values
						return null;						
					}
				}else {
					// these are truly different values
					return null;
				}
					
			}else {
				// these are truly different values
				return null;
			}
		}else {
			// neither one of them is an extension to the other
			return null;
		}
	}

	Map<String, String> cleanseDataMapping(Map<String, Map<String, Integer>> resultMap, Map<String, Map<String, Integer>> toBeFixed){
		Map<String, String> cleansedMapping = new HashMap<String, String>();
		Set<String> keySet = resultMap.keySet();
		// sort the keys so that we can see similarly looking keys
		List<String> sortedKeys = new ArrayList<String>(keySet);
	    Collections.sort(sortedKeys);
		Iterator<String> iter =  sortedKeys.iterator();
		while(iter.hasNext()) {
			String key = iter.next();
			Map<String, Integer> value = resultMap.get(key);
			if(value.size() > 1) {
				LOGGER.debug("For key " + key);
				Set<String> keySet2 = value.keySet();  
				Iterator<String> iter2 =  keySet2.iterator();
				int total = 0;
				while(iter2.hasNext()) {
					String value2 = iter2.next();
					Integer counter = value.get(value2);
					total += counter.intValue();
					LOGGER.debug(" value " + value2 + " has instances " + counter.intValue());
				}
				Iterator<String> iter3 =  keySet2.iterator();
				double maxProbability = 0.0;
				String keyWithMaxProbability = null;
				while(iter3.hasNext()) {
					String value2 = iter3.next();
					Integer counter = value.get(value2);
					double probability =  (counter.intValue())/(double)total;
					if(probability == maxProbability) {
						keyWithMaxProbability =  ":";
					}else if(probability > maxProbability) {
						maxProbability =  probability;
						keyWithMaxProbability =  value2;
					}
					LOGGER.debug(" value " + value2 + " has probability of  " + probability);
				}
				
				// now set the value to the string with max probability
				if(!keyWithMaxProbability.equalsIgnoreCase(":")) {
					cleansedMapping.put(key,  keyWithMaxProbability);
				}else {
					LOGGER.info("we still need to fix this -  " + key);
					if(toBeFixed.containsKey(key)) {
						//nothing to be done here
					}else {
						toBeFixed.put(key, value);		
					}
				}
			}
		}
		return cleansedMapping;
	}
	
	public void printResultMap(Map<String, Map<String, Integer>> resultMap) {
		Set<String> keySet = resultMap.keySet();
		List<String> sortedKeys =  new ArrayList<String>(keySet);
		Collections.sort(sortedKeys);
		Iterator<String> iter = sortedKeys.iterator();
		while(iter.hasNext()) {
			LOGGER.info(iter.next());
			//System.out.println(iter.next());
			/*
			String key =  iter.next();
			//get the value map
			Map<String, Integer> valueMap = resultMap.get(key);
			Set<String> innerKeySet = valueMap.keySet();
			if(innerKeySet.size()> 1) {
				LOGGER.info(" Key -  " + key);
				Iterator<String> innerIter = innerKeySet.iterator();
				while(innerIter.hasNext()) {
					String temp  = innerIter.next();
					LOGGER.info(" ========> value  - " + temp + " -> " + valueMap.get(temp));				
				}
			}
			*/
		}
	}

	public void generateKnowledgeBase(String fileName, Map<String, Map<String, Integer>> resultMap) {
		if(resultMap == null || resultMap.isEmpty()) return;
		
		try {
			File myFile =  new File(fileName);
			FileWriter fileWriter =  new FileWriter(myFile);
			BufferedWriter bufferedWriter =  new BufferedWriter(fileWriter);
			Set<String> keySet = resultMap.keySet();
			List<String> sortedKeys =  new ArrayList<String>(keySet);
			Collections.sort(sortedKeys);
			Iterator<String> iter = sortedKeys.iterator();
			while(iter.hasNext()) {
				String key = iter.next();
				bufferedWriter.write(key);
				bufferedWriter.write("=======");
				Map<String, Integer> valueMap =  resultMap.get(key);
				Set<String> innerKeys = valueMap.keySet();
				if(innerKeys.size() == 1) {
					Iterator<String> innerIter = innerKeys.iterator();
					bufferedWriter.write(innerIter.next());
				}
				bufferedWriter.newLine();
				//System.out.println(iter.next());
				/*
				String key =  iter.next();
				//get the value map
				Map<String, Integer> valueMap = resultMap.get(key);
				Set<String> innerKeySet = valueMap.keySet();
				if(innerKeySet.size()> 1) {
					LOGGER.info(" Key -  " + key);
					Iterator<String> innerIter = innerKeySet.iterator();
					while(innerIter.hasNext()) {
						String temp  = innerIter.next();
						LOGGER.info(" ========> value  - " + temp + " -> " + valueMap.get(temp));				
					}
				}
				*/
			}
			bufferedWriter.flush();
			bufferedWriter.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public void printMap(Map<String, String> dataMapping) {
		Set<String> keySet = dataMapping.keySet();
		List<String> sortedKeys =  new ArrayList<String>(keySet);
		Collections.sort(sortedKeys);
		Iterator<String> iter = sortedKeys.iterator();
		while(iter.hasNext()) {
			String key =  iter.next();
			LOGGER.debug(" key - " + key + " -> " + dataMapping.get(key));
		}
	}
	
	public void saveDataMapping(Map<String, String> myMap, String fileName) {
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet("DataMapping");
		
		XSSFCreationHelper helper = workbook.getCreationHelper();
		XSSFCellStyle dateStyle =  workbook.createCellStyle();
		dateStyle.setDataFormat(helper.createDataFormat().getFormat("m/d/yy"));
		
		XSSFCellStyle currencyStyle = workbook.createCellStyle();
		currencyStyle.setDataFormat(helper.createDataFormat().getFormat("$#,##0.00_);[Red]($#,##0.00)"));
		
		//create a header row
		XSSFRow headerRow = sheet.createRow(0);
		XSSFCell headerCell1 = headerRow.createCell(0);
		headerCell1.setCellValue("transaction Key");
		XSSFCell headerCell2 = headerRow.createCell(1);
		headerCell2.setCellValue("Category");
		XSSFCell headerCell3 = headerRow.createCell(2);
		headerCell3.setCellValue("Sub1");
		XSSFCell headerCell4 = headerRow.createCell(3);
		headerCell4.setCellValue("Sub2");
		XSSFCell headerCell5 = headerRow.createCell(4);
		headerCell5.setCellValue("Sub3");
		XSSFCell headerCell6 = headerRow.createCell(5);
		headerCell6.setCellValue("Sub4");
		
		//sort the keys and save
		Set<String> keySet =  myMap.keySet();
		List<String> sortedKeys = new ArrayList<String>(keySet);
		Collections.sort(sortedKeys);
		Iterator<String> iter =  sortedKeys.iterator();
		int rowNum =  0;
		while(iter.hasNext()) {
			String thisKey = iter.next();
			String thisValue = myMap.get(thisKey);
			
			XSSFRow row = sheet.createRow(rowNum+1);
			XSSFCell cell1 =  row.createCell(0);
			cell1.setCellValue(thisKey);

			//need to split thisVaue
			String[] fields = thisValue.split("|");
			if(fields.length >= 1 ) {
				XSSFCell cell2 =  row.createCell(1);
				cell2.setCellValue(fields[0]);		
			}
			if(fields.length >= 2 ) {
				XSSFCell cell3 =  row.createCell(2);
				cell3.setCellValue(fields[1]);		
			}
			if(fields.length >= 3 ) {
				XSSFCell cell4 =  row.createCell(3);
				cell4.setCellValue(fields[2]);		
			}
			if(fields.length >= 4 ) {
				XSSFCell cell5 =  row.createCell(4);
				cell5.setCellValue(fields[3]);		
			}
			rowNum++;
		}
		
		try {
			FileOutputStream outputStream = new FileOutputStream(fileName);
			workbook.write(outputStream);
		}catch(Exception e) {
			e.printStackTrace();
		}
		try{
			workbook.close();
		}catch(IOException e) {
			e.printStackTrace();
		}
	}
	
	public Map<String, Map<String, Integer>> prepareReverseMapping(List<LedgerRecord> records){
		Map<String, Map<String, Integer>> reverseMap = null;
		if(records == null || records.size() <=0 ) return reverseMap;

		reverseMap = new HashMap<String, Map<String, Integer>>();
		Iterator<LedgerRecord> iter = records.iterator();
		while(iter.hasNext()){
			LedgerRecord rec = (LedgerRecord)iter.next();

			if(rec.getCategory() == null || ((rec.getCategory()).trim()).length() <= 0)
				continue;
			
			StringBuffer keyBuf = new StringBuffer();
			//prepare the key
			keyBuf.append(rec.getCategory().trim());
			if(rec.getSub1() != null && ((rec.getSub1()).trim()).length() > 0) {
				keyBuf.append(myDelimiter);
				keyBuf.append(rec.getSub1().trim());
				if(rec.getSub2() != null && ((rec.getSub2()).trim()).length() > 0) {
					keyBuf.append(myDelimiter);
					keyBuf.append(rec.getSub2().trim());
					if(rec.getSub3() != null && ((rec.getSub3()).trim()).length() > 0) {
						keyBuf.append(myDelimiter);
						keyBuf.append(rec.getSub3().trim());
						if(rec.getSub4() != null && ((rec.getSub4()).trim()).length() > 0) {
							keyBuf.append(myDelimiter);
							keyBuf.append(rec.getSub4().trim());
						}
					}
				}
			}
			String key = keyBuf.toString();
			if(reverseMap.containsKey(key)) {
				Map<String, Integer> valueMap = reverseMap.get(key);
				String innerKey = rec.getDescription();
				if(valueMap.containsKey(innerKey)) {
					// nothing to do here.
				}else {
					Map<String, Integer> valueMap2 = new HashMap<String, Integer>();
					valueMap2.put(innerKey, 1);
					reverseMap.put(key, valueMap2);
				}
			}else {
				Map<String, Integer> valueMap = new HashMap<String, Integer>();
				valueMap.put(rec.getDescription(), 1);
				reverseMap.put(key, valueMap);
			}
		}
		return reverseMap;
	}
	
	public void printReverseMap(Map<String, Map<String, Integer> > reverseMap ) {
		Set<String> keys = reverseMap.keySet();
		List<String> sortedKeys =  new ArrayList<String>(keys);
		Collections.sort(sortedKeys);
		Iterator<String> iter = sortedKeys.iterator();
		while(iter.hasNext()) {
			String key = iter.next();
			LOGGER.info(" ---> " + key);
			Map<String, Integer> valueMap = reverseMap.get(key);
			Set<String> transKeys = valueMap.keySet();
			List<String> sortedTransKeys = new ArrayList<String>(transKeys);
			Collections.sort(sortedTransKeys);
			Iterator<String> iter2 = sortedTransKeys.iterator();
			while(iter2.hasNext()) {
				LOGGER.info("     " + iter2.next());
			}
		}		
	}
	
	public Map<String, Map<String, Integer>> buildTokenFrequency(List<LedgerRecord> records){
		if(records == null || records.isEmpty()) return null;
		
		Map<String, Map<String, Integer>> tokenizer =  new HashMap<String, Map<String, Integer>>();
		
		Iterator<LedgerRecord> iter = records.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec =  iter.next();
			
			StringBuffer keyBuf = new StringBuffer();
			//prepare the key
			keyBuf.append(rec.getCategory().trim());
			if(rec.getSub1() != null && ((rec.getSub1()).trim()).length() > 0) {
				keyBuf.append(myDelimiter);
				keyBuf.append(rec.getSub1().trim());
				if(rec.getSub2() != null && ((rec.getSub2()).trim()).length() > 0) {
					keyBuf.append(myDelimiter);
					keyBuf.append(rec.getSub2().trim());
					if(rec.getSub3() != null && ((rec.getSub3()).trim()).length() > 0) {
						keyBuf.append(myDelimiter);
						keyBuf.append(rec.getSub3().trim());
						if(rec.getSub4() != null && ((rec.getSub4()).trim()).length() > 0) {
							keyBuf.append(myDelimiter);
							keyBuf.append(rec.getSub4().trim());
						}
					}
				}
			}
			String key = keyBuf.toString();
			
			String[] words =  (rec.getDescription()).split(" ");
			for(int i = 0; i < words.length; i++) {
				//check if this word is consequential for categorizing the transactions
				if(!IsConsequential(words[i])) continue;
					
				if(tokenizer.containsKey(words[i])) {
					Map<String, Integer> frequency =  tokenizer.get(words[i]);
					if(frequency.containsKey(key)) {
						Integer count = frequency.get(key);
						count = count + 1;
						frequency.put(key, count);
						tokenizer.put(words[i], frequency);
					}else {
						frequency.put(key, 1);
						tokenizer.put(words[i], frequency);						
					}
				}else {
					Map<String, Integer> frequency =  new HashMap<String, Integer>();
					frequency.put(key, 1);
					tokenizer.put(words[i], frequency);
				}
			}
		}
		return tokenizer;
	}
	
	public boolean IsConsequential(String word) {
		if(word == null || (word.trim()).length() <= 0) return false;
		return true;
	}
	
	public void printTokenizer(Map<String, Map<String, Integer>> tokenizer) {
		Set<String> keySet =  tokenizer.keySet();
		ArrayList<String> sortedKeys = new ArrayList<String>(keySet);
		Collections.sort(sortedKeys);
		Iterator<String> iter =  sortedKeys.iterator();
		while(iter.hasNext()) {
			String word  = iter.next();
			//LOGGER.info(word);
			System.out.println(word);
			/*
			Map<String, Integer> frequency =  tokenizer.get(word);
			Set<String> innerKeySet = frequency.keySet();
			ArrayList<String> sortedInnerKeys = new ArrayList<String>(innerKeySet);
			Collections.sort(sortedInnerKeys);
			Iterator<String> iter2 =  sortedInnerKeys.iterator();
			while(iter2.hasNext()) {
				String key =  iter2.next();
				Integer counter = frequency.get(key);
				LOGGER.info("----> has "+ counter.intValue() + " instances for " + key);
			}
			*/
		}
	}
	
	public Map<String, Integer> buildFrequencyMap(List<LedgerRecord> records){
		Map<String, Integer> frequencyMap = null;
		if(records == null || records.size() <= 0 ) return frequencyMap;
		
		frequencyMap =  new HashMap<String, Integer>();
		
		Iterator<LedgerRecord> iter = records.iterator();
		while(iter.hasNext()){
			LedgerRecord rec = (LedgerRecord)iter.next();

			if(rec.getCategory() == null || ((rec.getCategory()).trim()).length() <= 0)
				continue;
			
			StringBuffer keyBuf = new StringBuffer();
			//prepare the key
			keyBuf.append(rec.getCategory().trim());
			if(rec.getSub1() != null && ((rec.getSub1()).trim()).length() > 0) {
				keyBuf.append(myDelimiter);
				keyBuf.append(rec.getSub1().trim());
				if(rec.getSub2() != null && ((rec.getSub2()).trim()).length() > 0) {
					keyBuf.append(myDelimiter);
					keyBuf.append(rec.getSub2().trim());
					if(rec.getSub3() != null && ((rec.getSub3()).trim()).length() > 0) {
						keyBuf.append(myDelimiter);
						keyBuf.append(rec.getSub3().trim());
						if(rec.getSub4() != null && ((rec.getSub4()).trim()).length() > 0) {
							keyBuf.append(myDelimiter);
							keyBuf.append(rec.getSub4().trim());
						}
					}
				}
			}
			String key = keyBuf.toString();
			if(frequencyMap.containsKey(key)) {
				Integer value = frequencyMap.get(key);
				value = value + 1;
				frequencyMap.put(key, value);
			}else {
				Integer value = 1;
				frequencyMap.put(key, value);
			}
		}
		
		return frequencyMap;
	}
	
	public void printFrequency(Map<String, Integer> frequencyMap) {
		if(frequencyMap == null || frequencyMap.isEmpty()) return;
		
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(frequencyMap.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
		    @Override
		    public int compare(Map.Entry<String, Integer> entry1, Map.Entry<String, Integer> entry2) {
		        return entry1.getValue().compareTo(entry2.getValue());
		    }
		});
		
		frequencyMap.entrySet().stream()
		   .sorted(Map.Entry.comparingByValue())
		   .forEach(System.out::println);
	}
	
	public static void main(String[] args) {
		//expected arguments are filename, sheet name
		ReadLedgerFile process = new ReadLedgerFile();
		String fileName =  "D:\\fin_docs\\updated_ledger_01192026_almost_final.xlsx";
		String sheetName =  "Runbook";
		try {
			LOGGER.info("Reading "+ fileName + " and sheet " + sheetName);
			List<LedgerRecord> records =  process.readFile2(fileName, sheetName);
			LOGGER.info("Read " + records.size() );
			
			List<String> excludeList = List.of(
					"DIRECT DEP",
					"TRANSFER",
					"INTEREST",
					"DEPOSITORY"
					);
			//simply display the unique descriptions
			Map<String, Integer> descStats = new HashMap<String, Integer>();
			for(int i =  0; i <records.size(); i++) {
				LedgerRecord rec =  records.get(i); 
				String key =  rec.getDescription();
	            //key = key.replaceAll("[^A-Z0-9\\s]", " ");
	            //key = key.replaceAll("\\s+", " ").trim();
	            boolean doExclude =  false;
	            for (String element : excludeList) {
	            	doExclude =
	            		    Pattern.compile(element, Pattern.CASE_INSENSITIVE)
	            		           .matcher(key)
	            		           .find();
	            }
	            if(!doExclude) {
					if(descStats.containsKey(key)) {
						Integer count = descStats.get(key);
						count = count + 1;
						descStats.put(key, count);
					}else {
						descStats.put(key, 1);
					}
	            }
			}
			
			// now sort it
			Map<String, Integer> sorted =
			        descStats.entrySet()
			           .stream()
			           .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
			           .collect(
			               LinkedHashMap::new,
			               (m, e) -> m.put(e.getKey(), e.getValue()),
			               LinkedHashMap::putAll
			           );
			// now print
			for (Map.Entry<String, Integer> e : sorted.entrySet()) {
			    System.out.println(e.getKey() + " = " + e.getValue());
			}
			/*
			List<LedgerRecord> ledgerWithCategory = new ArrayList<LedgerRecord>();
			for(int i =  0; i < records.size(); i++) {
				LedgerRecord rec = records.get(i);
				if(rec.getCategory() != null && rec.getCategory().trim().length() > 0) {
					ledgerWithCategory.add(rec);
				}
			}
			*/
			/*
			// now save the new array list
			Path outputFile =  Path.of("D:\\fin_docs\\sample_ledger_category.xlsx");
			try {
				LedgerExcelWriter.writeLedger(ledgerWithCategory, outputFile);
			}catch(Exception e) {
				e.printStackTrace();
			}
			*/
			/*
			Map<String, Integer> frequencyMap = process.buildFrequencyMap(records);
			process.printFrequency(frequencyMap);
			*/
			//lets try to build the knowledge base
			//tokenize all the transactions and see which keys map to the categories
			//Map<String, Map<String, Integer>> tokenizer = process.buildTokenFrequency(records);
			//process.printTokenizer(tokenizer);
			
			/*
			// lets build a map for ML data mapping
			Map<String, Map<String, Integer>> resultMap =  process.prepareMap(records);
			LOGGER.info("built a map of " + resultMap.size());
			//process.printResultMap(resultMap);
			process.generateKnowledgeBase("C:\\Users\\annam\\OneDrive\\Documents\\rule_config.json", resultMap);
			*/
			
			/*
			//lets build the reverse mapping
			Map<String, Map<String, Integer>> reverseMap  = process.prepareReverseMapping(records);
			
			// print the map
			process.printReverseMap(reverseMap);
			*/
			
			/*
			//cleanse the map that was built so far
			Map<String, Map<String, Integer>> toBeFixed =  new HashMap<String, Map<String, Integer>>();
			Map<String, String> cleansedMapping = process.cleanseDataMapping(resultMap, toBeFixed);
			
			//print the cleansed mapping
			process.printMap(cleansedMapping);
			
			//write this to a file with the new mapping
			process.saveDataMapping(cleansedMapping, "C:\\Users\\annam\\OneDrive\\Documents\\cleansed_mapping.xlsx");
			*/
		}catch(FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
