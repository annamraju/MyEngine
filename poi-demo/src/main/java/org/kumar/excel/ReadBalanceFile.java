/**
 * 
 */
package org.kumar.excel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.text.SimpleDateFormat;
import java.text.ParseException;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kumar.excel.util.Account;
import org.kumar.excel.util.BalanceRecord;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import java.util.Locale;
/**
 * 
 */
public class ReadBalanceFile {

	private static final Logger LOGGER = LogManager.getLogger("READBALANCEFILE");

	private static final String JSON_OUTPUT_FOLDER = "D:\\fin_docs\\generated_json";
	
	// all the columns in balance excel file
	private static final String[][] EXCEL_COLUMNS = {
            {"Cash","GTE fcu","Checking","Kumar & Padma","USD"}, // column D
            {"Cash","BOA","Checking","Kumar & Padma","USD"},  // E
            {"Cash","GTE fcu","Savings","Kumar & Padma","USD"}, // F
            {"Cash","BOA","Savings","Kumar & Padma","USD"}, // G
            {"Cash","BOA","Checking","Ananya","USD"}, // H
            {"Cash","BOA","Checking","Anjali","USD"}, // I
            {"Cash","etrade","Premium Savings","Kumar","USD"}, // J
            {"Cash","Chase","Checking","Kumar & Padma","USD"}, // K
            {"Cash","Marcus","Savings","Kumar","USD"}, // L
            {"Cash","GTE fcu","CDs","Kumar & Padma","USD"}, // M
            {"Cash","GTE fcu","6 month CD","Kumar & Padma","USD"}, // N
            {"Cash","GTE fcu","6 month Jumbo","Kumar & Padma","USD"}, // O
            {"Cash","GTE fcu","12 month Jumbo","Kumar & Padma","USD"}, // P
            {"Cash", "Webster Bank", "Savings", "Kumar", "USD"}, // Q	            
            {"Cash", "Total", "Total", "Total", "USD"}, // R
            {"Donations","HSBC","Savings","Kumar","USD"}, // S
            {"Donations","Citizens","Savings","Kumar","USD"}, // T
            {"Donations","Citi","Savings","Dontations","USD"}, // U
            {"Donations", "Total", "Total", "Total", "USD"},	   // V	            
            {"Investments","etrade","Brokerage","Kumar","USD"}, // W
            {"Investments","Fidelity","Brokerage","Padma","USD"}, // X
            {"Investments","Coinbase","Crypto","Kumar","USD"}, // Y
            {"Investments", "Total", "Total", "Total", "USD"},	    // Z	            
            {"Education","TRowPrice","529 Plan","Ananya","USD"}, // AA
            {"Education","Fidelity","529 Plan","Anjali","USD"},// AB
            {"Education","Florida","College Prepaid","Ananya","USD"}, // AC
            {"Education","Florida","College Prepaid","Anjali","USD"}, // AD
            {"Education", "Total", "Total", "Total", "USD"},	// AE   	            
            {"HSA","Optum Bank","HSA","Kumar","USD"}, // AF
            {"Retirement","Fidelity","Verion 401k","Kumar","USD"}, // AG
            {"Retirement","Trowprice","Syneverse 401k","Padma","USD"}, //AH
            {"Retirement","New York Life","Lebor & Friedman 401k","Padma","USD"}, // AI
            {"Retirement","JPMC","401k","Padma","USD"}, // AJ
            {"Retirement","DTCC","401k","Kumar","USD"},  // AK
            {"Retirement","Vanguard","Infosys 401k","Padma","USD"}, // AL
            {"Retirement","Fidelity","Infosys 401k","Padma","USD"}, // AM
            {"Retirement","DTCC","Pension","Kumar","USD"}, // AN
            {"Retirement","Syneverse","Pension","Padma","USD"}, // AO
            {"Retirement","JPMC","Pension","Padma","USD"}, // AP
            {"Retirement","Fidelity","RolloverIRA","Kumar","USD"}, // AQ
            {"Retirement","Fidelity","Roth IRA","Kumar","USD"}, // AR
            {"Retirement","Fidelity","RolloverIRA","Padma","USD"}, // AS
            {"Retirement","Fidelity","Verizon LTI","Padma","USD"}, //AT
            {"Retirement","DTCC","Exec Savings Plan","Kumar","USD"}, // AU
            {"Retirement","DTCC","LTIP","Kumar","USD"}, // AV
            {"Retirement","Etrade","Roth IRA","Ananya","USD"}, // AW
            {"Retirement","Fidelity","Verizon 401k","Padma","USD"},// AX
            {"Retirement","Lebor & Friedman","401k","Padma","USD"},	   // AY          	            
            {"Retirement","Brightspeed","401k","Padma","USD"}, // AZ
            {"Retirement","Total","Total","Total","USD"}, // BA
            {"Overseas","ICICI","NRO","Kumar & Padma","INR"}, // BB
            {"Overseas","ICICI","NRE","Kumar & Padma","INR"}, // BC
            {"Overseas","SBI","NRO","Kumar","INR"}, // BD
            {"Overseas","SBI","NR Fixed Deposit","Kumar","INR"}, // BE
            {"Overseas","Corp/Union Bank","NRO","Kumar","INR"}, // BF
            {"Overseas","Corp/Union Bank","NRO","Padma","INR"}, // BG
            {"Overseas","ICICI","Prudential 1","Kumar","INR"}, //BH
            {"Overseas","ICICI","Prudential 2","Kumar","INR"}, // BI
            {"Overseas","Mahendra Kotak","LIC","Kumar","INR"}, // BJ
            {"Overseas","Total","Total","Total","INR"}, // BK
            {"Overseas","Total","Total","Total","USD"},	     // BL       
            {"BusinessAccounts","Chase","Anantha LLC","Kumar & Padma","USD"}, //BM
            {"BusinessAccounts","Chase","Idhayam Square LLC","Kumar & Padma","USD"}, // BN
            {"BusinessAccounts","Chase","Marutham Square LLC","Kumar & Padma","USD"}, // BO
            {"BusinessAccounts","Loan","Dandapani LLC","Kumar & Padma","USD"}, // BP
            {"BusinessAccounts","Total","Total","Total","USD"}, // BQ
            {"RealEstate","Meadow","home1","Kumar & padma","USD"}, // BR	            
            {"RealEstate","Mist","home2","Kumar & padma","USD"}, // BS	            
            {"RealEstate","Total","Total","Total","USD"}, // BT	            
            {"Assets","Total","Total","Total","USD"}, // BU
            {"Liabilities","BMW","Car loan","Kumar","USD"}, // BV	            
            {"Liabilities","BOA","CreditCard","Kumar","USD"}, // BW	            
            {"Liabilities","Chase","CreditCard","Padma","USD"}, // BX	            
            {"Liabilities","Chase Business","CreditCard","Anantha","USD"}, // BY	            
            {"Liabilities","Total","Total","Total","USD"}, // BZ	            
            {"Total","Total","Total","Total","USD"} // CA
        };

	/** these are the files that will be generated as json output
	 * 
	 */
	private static final Map<Integer, String> jsonName = Map.ofEntries(
			Map.entry(17, "cash"), //column R
			Map.entry(21, "donations"), // column V
			Map.entry(25, "investments"), // column Z
			Map.entry(30, "education"), // column AE
			Map.entry(31, "hsa"), //column AF
			Map.entry(52, "retirement"), // column BA
			Map.entry(63, "overseas"), // column BL
			Map.entry(68, "venture"), // column BQ
			Map.entry(71, "realestate"), //column BT
			Map.entry(72, "assetstotal"), //column BU
			Map.entry(77, "liabilities"), // column BZ
			Map.entry(78, "net") // column CA
			); 

	private static final Map<String, List<Integer> > total_of_totals = Map.ofEntries(
			// assets consists of the following to build a pie chart
			//R	    17	cash
			//V	    21	donations
			//Z	    25	investments
			//AE	30	education
			//AF	31	hsa
			//BA	52	retirement
			//BL	63	overseas
			//BQ	68	venture
			//BT	72	realestate
			//BU	75	assets
			//BZ	80	liabilities
			//CA	81	net
			Map.entry("assets", List.of(17, 21, 25, 30, 31, 52, 63, 68, 71)),
			Map.entry("liabilities", List.of(77)),
			Map.entry("net", List.of(72, 77))
			
			);
			
	private static Map<Integer, Account> metaAccounts =  new HashMap<Integer, Account>();
	static {
		int counter =  3; // first 3 columns not accounted for in the above static data
		for (String[] r : EXCEL_COLUMNS) {
            // Defensive trim in case source data had extra spaces 
			try {
				metaAccounts.put(counter, new Account(r[0].trim(), r[1].trim(), r[2].trim(), r[3].trim(), r[4].trim()));
				counter++;
			}catch(Exception e2) {
				e2.printStackTrace();
			}
        }		
	}
	
	
	/**
	 * Utility function to get cell text
	 * @param cell
	 * @param wb
	 * @return
	 */
    public static String getCellText(Cell cell, Workbook wb) {
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter(Locale.US); // or your locale
        FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();
        return formatter.formatCellValue(cell, evaluator);
    }
    
	/**
	 * Function to get a cell with a formula which results in a number
	 * @param cell
	 * @return
	 */
	public static int getFormulaResultNumeric(XSSFCell cell) {
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
	 * 
	 * @param cell
	 * @return
	 */
	public static double getFormulaResultDouble(XSSFCell cell) {
		double result = 0;
		if(cell.getCellType()== CellType.FORMULA) {
			switch (cell.getCachedFormulaResultType()) {
	        case NUMERIC:
	            result =  (double) (cell.getNumericCellValue());
	            break;
	        default:
	            System.out.println(cell.getRichStringCellValue());
	            break;
			}
		}
		return result;
	}	
	
	
	/**
	 * Main function to read a balance file
	 * @param inFile
	 * @param sheetName
	 * @return
	 * @throws FileNotFoundException
	 */
	public List<BalanceRecord> readBalanceFile(String inFile, String sheetName) throws FileNotFoundException{
		List<BalanceRecord> records =  new ArrayList<BalanceRecord>();
		
		File fileHandle =  null;
		FileInputStream reader = null; 
		XSSFWorkbook workbook = null;
		
		LOGGER.info(metaAccounts);
		try{
			fileHandle =  new File(inFile);
			reader = new FileInputStream (fileHandle);
			workbook = new XSSFWorkbook(reader);
			XSSFSheet mainSheet = workbook.getSheet(sheetName);

			LOGGER.info("lastrow num " + mainSheet.getLastRowNum());
			
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
			
			for(int rowNum = mainSheet.getFirstRowNum(); rowNum <= mainSheet.getLastRowNum(); rowNum++) { 
				LOGGER.info("reading " + rowNum);
				
				//first 4 rows are headers - just ignore them
				if(rowNum <= 3) continue;
				

				XSSFRow row = mainSheet.getRow(rowNum);

				XSSFCell cell0 =  row.getCell(0,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				//read it as a string
				//String rptDtStr = cell0.getStringCellValue();
				String rptDtStr =  ReadBalanceFile.getCellText(cell0, workbook);
				//now convert it to a date
				Date rptDate = new Date();
				try {
					rptDate = sdf.parse(rptDtStr);
				}catch(ParseException e3) {
					e3.printStackTrace();
				}
				//Date rptDate =  cell0.getDateCellValue();
				
				int monthNum = 0;
				XSSFCell cell1 =  row.getCell(1,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				if(cell1.getCellType()== CellType.FORMULA) {
					monthNum = getFormulaResultNumeric(cell1);
				}
				
				int yearNum = 0;
				XSSFCell cell2 =  row.getCell(2,Row.MissingCellPolicy.RETURN_NULL_AND_BLANK );
				if(cell2.getCellType()== CellType.FORMULA) {
					yearNum = getFormulaResultNumeric(cell2);
				}

				int totalColumns = EXCEL_COLUMNS.length; // total columns with balances other than the first 3 columns
				BalanceRecord rec =  null;
				for(int i  = 0; i<totalColumns; i++) {
					//System.out.println("processing i = " + i);
					XSSFCell tempCell = row.getCell(3+i, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK); 
					if(tempCell != null && tempCell.getCellType() != CellType.BLANK) {
						try {
							double cell3 =  0;
							if(tempCell.getCellType()== CellType.FORMULA) {
									cell3 = getFormulaResultDouble(tempCell);
									//LOGGER.info(" got formula double - " + cell3 + "  for " + (3+i));
							}else {
								cell3 =  tempCell.getNumericCellValue();
								//LOGGER.info(" got double - " + cell3 + "  for " + (3+i));
							}
							int key =  3+i;
							if(metaAccounts.containsKey(key)) {
								Account act1 =  (Account)(metaAccounts.get(key));
								if((act1.getName()).equalsIgnoreCase("Total")) {
									//LOGGER.info("for " + (key) + " " + act1 + " value " + cell3);
								}
								if(i==0) rec = new BalanceRecord(rptDate, monthNum, yearNum, act1, cell3);
								else rec.addActBalance(act1, cell3);
							}else {
								LOGGER.error("this index " + (3+i) + " does not have an account");
							}
						}catch(IllegalStateException e5) {
							System.out.println("exception for " + tempCell.getStringCellValue() );
							throw e5;
						}
					}else {
						//LOGGER.info("nothing captured here for column " + (i+3));
					}
				}
				
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
	 * @param records
	 */	
	public void generateStats(List<BalanceRecord> records) {
		for(Integer key : jsonName.keySet()) {
			generateStats(records, key.intValue(), jsonName.get(key) + ".json");
		}
	}
	
	/**
	 * 
	 * @param records
	 */
	public void generateStats(List<BalanceRecord> records, int excelColumn, String outputFile) {
		//get the account that was mapped to excelColumn
		Account act;
		if(metaAccounts.containsKey(excelColumn)) {
			act =  metaAccounts.get(excelColumn);
			Iterator<BalanceRecord> iter = records.iterator();
			List< Map<String, String> > stats  = new ArrayList< Map<String, String>>();
			while(iter.hasNext()) {
				BalanceRecord rec =  iter.next();
				Map<String, String> json = new HashMap<String, String>();
				json.put("date", rec.getDateString());
				json.put("Value", rec.getBalanceString(act));
				stats.add(json);
			}
			//dump it to a file
			writeJsonToFile(JSON_OUTPUT_FOLDER + "\\" + outputFile, stats);
		}else {
			System.out.println("unable to generate file " + outputFile);
		}
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
        LOGGER.info("JSON written to " + outputFile);
	}

	/**
	 * 
	 */
	public void generatePieChartDataFor(BalanceRecord record, String type, String suffix) {
		// pie chart data is a snapshot of net point in time
		if(type.equalsIgnoreCase("assets"))
			generatePieChartData(record, total_of_totals.get("assets"), "assets_piechart_"+ suffix + ".json");
		else if (type.equalsIgnoreCase("liabilities"))
			generatePieChartData(record, total_of_totals.get("assets"), "liabiltiies_piechart_" + suffix + ".json");
		else
			LOGGER.error("cannot handle this type - " + type);
	}
	
	/**
	 * 
	 * @param records
	 * @param excelColumn
	 * @param outputFile
	 */
	public void generatePieChartData(BalanceRecord record, List<Integer> subTotals, String outputFile) {
		LOGGER.info("sub totals is " + subTotals);
		Iterator<Integer> iter = subTotals.iterator();
		List< Map<String, String> > stats  = new ArrayList< Map<String, String>>();
		while(iter.hasNext()) {
			Integer indexObj =  iter.next();
			LOGGER.info("got " +  indexObj + " as indexObj");
			Map<String, String> json = new HashMap<String, String>();
			json.put("name", jsonName.get(indexObj));
			json.put("value", record.getBalanceString(metaAccounts.get(indexObj)));
			LOGGER.info("got name as " +  jsonName.get(indexObj));
			LOGGER.info("got value as " +  record.getBalanceString(metaAccounts.get(indexObj)));
			
			stats.add(json);
		}
		//dump it to a file
		writeJsonToFile(JSON_OUTPUT_FOLDER + "\\" + outputFile, stats);
	}
	
	
	public BalanceRecord getLatestBalance(List<BalanceRecord> records, int year){
		BalanceRecord result =  null;
		if(records == null || records.isEmpty())
			return result;
		
		Iterator<BalanceRecord> iter = records.iterator();
		while(iter.hasNext()) {
			BalanceRecord temp = iter.next();
			if(temp.getYear()== year){
				if(result == null || result.getMonth() > temp.getMonth())
					result = temp;
			}
		}
		
		return result;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		
		String inFile = "D:\\fin_docs\\latest all time balances.xlsx";
		String sheetName ="Main";
		ReadBalanceFile rbf = new ReadBalanceFile();
		try {
			List<BalanceRecord> x =  rbf.readBalanceFile( inFile, sheetName);
			
			// lets print
			Iterator<BalanceRecord> iter=  x.iterator();
			while(iter.hasNext()) {
				BalanceRecord bal =  iter.next();
				LOGGER.info(bal.specialToString());
			}
		
			rbf.generateStats(x);
			
			//generate data for pie chart
			//get the max value of a balance record by year
			
			int START_YEAR  = 2014;
			int MAX_YEAR =  2025;
			int runningYear =  START_YEAR;
			while(runningYear <= MAX_YEAR) {
				BalanceRecord y = rbf.getLatestBalance(x, runningYear);
				String suffix = Integer.toString(runningYear);
				rbf.generatePieChartDataFor(y, "assets", suffix);
				runningYear++;
			}
			
		}catch(FileNotFoundException e1) {
			e1.printStackTrace();
		}

	}

}
