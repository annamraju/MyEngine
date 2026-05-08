/**
 * 
 */
package org.kumar.excel;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kumar.excel.util.LedgerRecord;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.time.Month;
import java.util.*;
/**
 * 
 */
public class ExcelToJsonConverter {

	/**
	 * Return formula value as a number
	 * @param cell
	 * @return
	 */
	public int getFormulaResultNumeric(XSSFCell cell) {
		int result = 0;
		if(cell.getCellType()== CellType.FORMULA) {
			switch (cell.getCachedFormulaResultType()) {
	        case NUMERIC:
	            result =  (int) (cell.getNumericCellValue());
	            break;
	        default:
	            System.out.println(cell.getRichStringCellValue());
	            break;
			}
		}
		return result;
	}

	/**
	 * Return formula value as a string
	 * @param cell
	 * @return
	 */
	public String getFormulaResultString(XSSFCell cell) {
		String result = null;
		if(cell.getCellType()== CellType.FORMULA) {
			switch (cell.getCachedFormulaResultType()) {
	        case STRING:
	            result =  cell.getStringCellValue();
	            break;
	        default:
	            System.out.println(cell.getRichStringCellValue());
	            break;
			}
		}
		return result;
	}

	/**
	 * Read an excel file in this format
	 * 	- acct type, order, year, month, date, desc, amt, bal, category, sub1. sub2, sub3. sub4
	 * and return a list of the object
	 * 	LedgerRecord
	 * @param inFile
	 * @param sheetName
	 * @return
	 * @throws FileNotFoundException
	 */
	public List<LedgerRecord> readFile(String inFile, String sheetName) throws FileNotFoundException{
		List<LedgerRecord> records =  new ArrayList<LedgerRecord>();
		
		File fileHandle =  null;
		FileInputStream reader = null; 
		XSSFWorkbook workbook = null;
		
		try{
			fileHandle =  new File(inFile);
			reader = new FileInputStream (fileHandle);
			workbook = new XSSFWorkbook(reader);
			XSSFSheet mainSheet = workbook.getSheet(sheetName);

			System.out.println("lastrow num " + mainSheet.getLastRowNum());
			for(int rowNum = mainSheet.getFirstRowNum()+1; rowNum < mainSheet.getLastRowNum(); rowNum++) { 
				//System.out.println("reading " + rowNum);
				XSSFRow row = mainSheet.getRow(rowNum);
				// now read the columns
				// columns in the order are
				// account type, order, year(formula), month(formula), transDate, amount, balance,
				// category, sub category 1, sub category 2, sub category 3, sub category 4
				String accountType =  (row.getCell(0, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK)).getStringCellValue().trim();
				//System.out.println(" got " + accountType);
				int order = 0;
				XSSFCell cell2 =  row.getCell(1,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				if(cell2 != null && cell2.getCellType()== CellType.FORMULA) {
					try {
						order = getFormulaResultNumeric(cell2);
					}catch(IllegalStateException e3) {
						order =  0;
					}
				}
				int year = 0;
				XSSFCell cell3 =  row.getCell(2,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				if(cell3.getCellType()== CellType.FORMULA) {
					year = getFormulaResultNumeric(cell3);
				}
				String month = null;
				XSSFCell cell4 =  row.getCell(3,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				if(cell4.getCellType()== CellType.FORMULA) {
					month = getFormulaResultString(cell4);
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
				
				LedgerRecord rec = new LedgerRecord(accountType, (short)order, (short)year, Month.valueOf(month.toUpperCase()), transactionDate, 
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
	 * 
	 * @param filename
	 * @return
	 */
	public List<Map<String, String>> loadExcelIntoJsonList(String filename){
		FileInputStream fis;
		try {
			fis = new FileInputStream(filename);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
        Workbook workbook;
		try {
			workbook = new XSSFWorkbook(fis);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
        Sheet sheet = workbook.getSheet("Runbook");
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();

        List<Map<String, String>> jsonData = new ArrayList<>();

        Row headerRow = sheet.getRow(0);
        int numCols = headerRow.getPhysicalNumberOfCells();

        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Map<String, String> rowData = new HashMap<>();
            for (int j = 0; j < numCols; j++) {
                String key = headerRow.getCell(j).toString();
                String value = "";	
                Cell cell = row.getCell(j);
                if(cell == null) {
                    rowData.put(key, value);                	
                	continue;
                }
                CellType cellType = cell.getCellType();

                if (cellType == CellType.FORMULA) {
                    CellValue cellValue = evaluator.evaluate(cell);
                    switch (cellValue.getCellType()) {
                        case NUMERIC:
                            double num =  cellValue.getNumberValue();
                            value =  String.valueOf(num);
                            break;
                        default:
                            value =  cellValue.getStringValue();
                            break;
                    }
                }else {
                	value = row.getCell(j) != null ? row.getCell(j).toString() : "";
                }
                rowData.put(key, value);
            }
            jsonData.add(rowData);
        }

        try {
        	workbook.close();
        }catch(IOException e) {
        	e.printStackTrace();
        }
        return jsonData;
	}
	
	/**
	 * 
	 * @param outputFile
	 * @param jsonData
	 */
	public void writeJsonToFile(String outputFile, List<Map<String, String>> jsonData) {
        ObjectMapper mapper = new ObjectMapper();
        try {
			mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputFile), jsonData);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
        System.out.println("JSON written to " + outputFile);
	}
	
	/**
	 * This is to prepare an aggregate map given a list of LedgerRecords
	 * @param list
	 * @return
	 */
	public Map<String, Map<Month, Double>>  prepareCumulativeMap(List<LedgerRecord> list){
		Map<String, Map<Month, Double> > aggregate = new HashMap<String, Map<Month, Double>>();
		Iterator<LedgerRecord> iter = list.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec =  (LedgerRecord)(iter.next());
			
			if(rec.getCategory() == null || rec.getCategory().equalsIgnoreCase("Internal"))
				continue;
			
			String key = Short.toString(rec.getYear());
			if(aggregate.containsKey(key)) {
				Map<Month, Double> innerMap =  aggregate.get(key);
				Month innerKey = rec.getMonEnum();
				if(innerMap.containsKey(innerKey)) {
					Double amt = innerMap.get(innerKey);
					Double newamt =  amt + rec.getAmount();
					innerMap.put(innerKey, newamt);
				}else {
					innerMap.put(innerKey, rec.getAmount());
				}
				aggregate.put(key, innerMap);
			}else {
				Map<Month, Double> innerMap = new HashMap<Month, Double>();
				innerMap.put(rec.getMonEnum(), rec.getAmount());
				aggregate.put(key, innerMap);
			}				
		}
		
		System.out.println("the size of aggregate is " + aggregate.size());
		return aggregate;
	}
	
	/**
	 * prepare income monthly map
	 * @param list
	 * @return
	 */
	public Map<String, Map<Month, Double>>  prepareIncomeMap(List<LedgerRecord> list){
		Map<String, Map<Month, Double> > aggregate = new HashMap<String, Map<Month, Double>>();
		Iterator<LedgerRecord> iter = list.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec =  (LedgerRecord)(iter.next());
			
			if(rec.getCategory() == null || !rec.getCategory().equalsIgnoreCase("Income"))
				continue;
			
			String key = Short.toString(rec.getYear());
			if(aggregate.containsKey(key)) {
				Map<Month, Double> innerMap =  aggregate.get(key);
				Month innerKey = rec.getMonEnum();
				if(innerMap.containsKey(innerKey)) {
					Double amt = innerMap.get(innerKey);
					Double newamt =  amt + rec.getAmount();
					innerMap.put(innerKey, newamt);
				}else {
					innerMap.put(innerKey, rec.getAmount());
				}
				aggregate.put(key, innerMap);
			}else {
				Map<Month, Double> innerMap = new HashMap<Month, Double>();
				innerMap.put(rec.getMonEnum(), rec.getAmount());
				aggregate.put(key, innerMap);
			}				
		}
		
		System.out.println("the size of income aggregate is " + aggregate.size());
		return aggregate;
	}

	/**
	 * 
	 * @param list
	 * @return
	 */
	public Map<String, Map<Month, Double>>  prepareExpenseMap(List<LedgerRecord> list){
		Map<String, Map<Month, Double> > aggregate = new HashMap<String, Map<Month, Double>>();
		Iterator<LedgerRecord> iter = list.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec =  (LedgerRecord)(iter.next());
			
			if(rec.getCategory() == null || !rec.getCategory().equalsIgnoreCase("Expense"))
				continue;
			
			String key = Short.toString(rec.getYear());
			if(aggregate.containsKey(key)) {
				Map<Month, Double> innerMap =  aggregate.get(key);
				Month innerKey = rec.getMonEnum();
				if(innerMap.containsKey(innerKey)) {
					Double amt = innerMap.get(innerKey);
					Double newamt =  amt + rec.getAmount();
					innerMap.put(innerKey, newamt);
				}else {
					innerMap.put(innerKey, rec.getAmount());
				}
				aggregate.put(key, innerMap);
			}else {
				Map<Month, Double> innerMap = new HashMap<Month, Double>();
				innerMap.put(rec.getMonEnum(), rec.getAmount());
				aggregate.put(key, innerMap);
			}				
		}
		
		System.out.println("the size of expense aggregate is " + aggregate.size());
		return aggregate;
	}

	/**
	 * 
	 * @param list
	 * @return
	 */
	public Map<String, Map<Month, Double>>  prepareOverseasMap(List<LedgerRecord> list){
		Map<String, Map<Month, Double> > aggregate = new HashMap<String, Map<Month, Double>>();
		Iterator<LedgerRecord> iter = list.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec =  (LedgerRecord)(iter.next());
			
			if(rec.getCategory() == null || !rec.getCategory().equalsIgnoreCase("Overseas"))
				continue;
			
			String key = Short.toString(rec.getYear());
			if(aggregate.containsKey(key)) {
				Map<Month, Double> innerMap =  aggregate.get(key);
				Month innerKey = rec.getMonEnum();
				if(innerMap.containsKey(innerKey)) {
					Double amt = innerMap.get(innerKey);
					Double newamt =  amt + rec.getAmount();
					innerMap.put(innerKey, newamt);
				}else {
					innerMap.put(innerKey, rec.getAmount());
				}
				aggregate.put(key, innerMap);
			}else {
				Map<Month, Double> innerMap = new HashMap<Month, Double>();
				innerMap.put(rec.getMonEnum(), rec.getAmount());
				aggregate.put(key, innerMap);
			}				
		}
		
		System.out.println("the size of overseas aggregate is " + aggregate.size());
		return aggregate;
	}

	/**
	 * 
	 * @param list
	 * @return
	 */
	public Map<String, Map<Month, Double>>  prepareDonationMap(List<LedgerRecord> list){
		Map<String, Map<Month, Double> > aggregate = new HashMap<String, Map<Month, Double>>();
		Iterator<LedgerRecord> iter = list.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec =  (LedgerRecord)(iter.next());
			
			if(rec.getCategory() == null || !rec.getCategory().equalsIgnoreCase("Donation"))
				continue;
			
			String key = Short.toString(rec.getYear());
			if(aggregate.containsKey(key)) {
				Map<Month, Double> innerMap =  aggregate.get(key);
				Month innerKey = rec.getMonEnum();
				if(innerMap.containsKey(innerKey)) {
					Double amt = innerMap.get(innerKey);
					Double newamt =  amt + rec.getAmount();
					innerMap.put(innerKey, newamt);
				}else {
					innerMap.put(innerKey, rec.getAmount());
				}
				aggregate.put(key, innerMap);
			}else {
				Map<Month, Double> innerMap = new HashMap<Month, Double>();
				innerMap.put(rec.getMonEnum(), rec.getAmount());
				aggregate.put(key, innerMap);
			}				
		}
		
		System.out.println("the size of donation aggregate is " + aggregate.size());
		return aggregate;
	}

	/**
	 * To convert monthly aggregate data to JSON data for graph display in react
	 * @param monthlyData
	 * @return
	 */
	public List<Map<String, String>> convertToNameValueJsonData(Map<Month, Double> monthlyData){
		List<Map<String, String>> jsonData =  new ArrayList<Map<String, String>>();
		
		Set<Month> keySet = monthlyData.keySet();
		List<Month> monthList =  new ArrayList<>(keySet);
		Collections.sort(monthList);
		
		Iterator<Month> iter =  monthList.iterator();
		while(iter.hasNext()) {
			Month mon = iter.next();
			Double amt = monthlyData.get(mon);
			Map<String, String> jsonRow = new HashMap<String, String>();
			jsonRow.put("name", mon.toString());
			//lets convert into a long.
			int amtInt = amt.intValue();
			jsonRow.put("value", String.valueOf(amtInt));
			jsonData.add(jsonRow);
		}
		return jsonData;
	}
	
	/**
	 * 
	 * @param fileName
	 * @param sheetName
	 * @param outputFolder
	 */
	public void prepareYearlyMonthlyFiles(String fileName, String sheetName, String outputFolder) {
		try {
			List<LedgerRecord> list = readFile(fileName, sheetName);
	
			// STEP1 : create income files
			Map<String, Map<Month, Double> > incomeAggregate = prepareIncomeMap(list);
			for( String yearKey : incomeAggregate.keySet()) {
				Map<Month, Double> monthlyMap =  incomeAggregate.get(yearKey);
				List<Map<String, String>> jSonData = convertToNameValueJsonData(monthlyMap);
				
				// create file with the year
				String outputFile = outputFolder + "\\income_" + yearKey + ".json";
				writeJsonToFile(outputFile, jSonData);
			}
			
			// STEP2 : create expense files
			Map<String, Map<Month, Double> > expenseAggregate = prepareExpenseMap(list);
			Set<String> keySet = expenseAggregate.keySet();
			for( String yearKey : keySet) {
				Map<Month, Double> monthlyMap =  expenseAggregate.get(yearKey);
				List<Map<String, String>> jSonData = convertToNameValueJsonData(monthlyMap);
				
				// create file with the year
				String outputFile = outputFolder + "\\expense_" + yearKey + ".json";
				writeJsonToFile(outputFile, jSonData);
			}

			//STEP3:  create overseas files
			Map<String, Map<Month, Double> > overseasAggregate = prepareOverseasMap(list);
			//now for each key, create a new file for Json data
			for( String yearKey : overseasAggregate.keySet()) {
				Map<Month, Double> monthlyMap =  overseasAggregate.get(yearKey);
				List<Map<String, String>> jSonData = convertToNameValueJsonData(monthlyMap);
				
				// create file with the year
				String outputFile = outputFolder + "\\overseas_" + yearKey + ".json";
				writeJsonToFile(outputFile, jSonData);
			}

			//STEP4:  create donation files
			Map<String, Map<Month, Double> > donationAggregate = prepareDonationMap(list);
			//now for each key, create a new file for Json data
			for( String yearKey : donationAggregate.keySet()) {
				Map<Month, Double> monthlyMap =  donationAggregate.get(yearKey);
				List<Map<String, String>> jSonData = convertToNameValueJsonData(monthlyMap);
				
				// create file with the year
				String outputFile = outputFolder + "\\donation_" + yearKey + ".json";
				writeJsonToFile(outputFile, jSonData);
			}

		}catch(Exception e) {
			e.printStackTrace();
		}
				
	}
	
	/** Main
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ExcelToJsonConverter mainClass =  new ExcelToJsonConverter();
		String fileName = "D:\\fin_docs\\Consolidated Ledger v2_mar2025.xlsx";
		String sheetName =  "Runbook";
		String outputFolder =  "D:\\fin_docs\\Yearly\\";
		mainClass.prepareYearlyMonthlyFiles(fileName, sheetName, outputFolder);
		//List<Map<String, String>> jsonData = mainClass.loadExcelIntoJsonList(fileName);

		//String outputfile = "D:\\fin_docs\\MyLedgerData.json";
		//mainClass.writeJsonToFile(outputfile, jsonData);
	}
}
