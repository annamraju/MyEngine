package org.kumar.excel;

//import org.apache.poi.xssf.usermodel.*;
import org.kumar.excel.util.Account;
import org.kumar.excel.util.AccountType;
import org.kumar.excel.util.DateWiseFinSummary;

import java.util.*;
import java.io.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.net.URL;

public class MyTestexcelReadWrite {

	private HashMap<Date, DateWiseFinSummary> _myFinData = null;
	private HashMap<Integer, Date> _columnDateMapping = null;
	private HashMap<Integer, Account> _columnAccountMapping = null;
	private List<Account> _allAccounts = null;
	
	private static final Logger LOGGER = LogManager.getLogger(MyTestexcelReadWrite.class);
	//private static final Logger LOGGER = LogManager.getRootLogger();
	
	public static final Logger getLogger() {
		return LOGGER;
	}

	public MyTestexcelReadWrite() {
		_myFinData =  new HashMap<Date, DateWiseFinSummary>();
		_columnDateMapping = new HashMap<Integer, Date>();
		_columnAccountMapping = new HashMap<Integer, Account>();
		_allAccounts = null;
	}
	
	public List<Account> getAllAccounts(){
		return _allAccounts;
	}

	public void loadAllAccounts() {
		String acctFileName =  "Account.metadata";
		try{
			URL accountResource = MyTestexcelReadWrite.class.getClassLoader().getResource(acctFileName);
			if(accountResource != null) {
				File accountFile = new File(accountResource.toURI());
				_allAccounts =  Account.loadAccountsFromFile(accountFile.getParent(), accountFile.getName());
			}else {
				String acctPath =  "c:\\users\\annam\\eclipse-workspace\\myexcelproject\\resources\\";
				_allAccounts =  Account.loadAccountsFromFile(acctPath, acctFileName);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	public static String[] getLedgerFilesToTranspose(){
		String[] files = new String[] { 
				"C:\\ledger_copy\\ledger_2014_v1.xlsx",
				"C:\\ledger_copy\\ledger_2015.xlsx",
				"C:\\ledger_copy\\ledger_2016 v2.xlsx",
				"C:\\ledger_copy\\ledger_2017_updated.xlsx",
				"C:\\ledger_copy\\ledger_2018_updated v3.xlsx",
				"C:\\ledger_copy\\ledger_2019_updated v3.xlsx",
				"C:\\ledger_copy\\ledger_2020_updated v3.xlsx",
				"C:\\ledger_copy\\ledger_2021_updated v3.xlsx",
				"C:\\ledger_copy\\ledger_2022_updated v3.xlsx",
				"C:\\ledger_copy\\ledger_2023_updated v3.xlsx"
				};
		return files;
	}
	
	public static void main(String[] args) throws Exception{
		
		System.out.println("System property is - " + System.getProperty("log4j.configuration"));
		//Logger logger = LogManager.getRootLogger();
    	//logger.trace("Configuration File Defined To Be :: "+System.getProperty("log4j.configurationFile"));

		MyTestexcelReadWrite myCode = new MyTestexcelReadWrite();

		// load account metadata file
		String acctPath =  "c:\\users\\annam\\eclipse-workspace\\myexcelproject\\resources\\";
		String acctFileName =  "Account.metadata";
		try{
			myCode._allAccounts =  Account.loadAccountsFromFile(acctPath, acctFileName);
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}

		String sheetName = "Main";
		String[] files = getLedgerFilesToTranspose();
		for(int iCount= 0; iCount <files.length; iCount++) {		
			//this will populate _myFinData;
			myCode.readLedgerMain(files[iCount], sheetName);
			LOGGER.info("read Ledger main is done");
			LOGGER.info("got " + myCode._myFinData.keySet().size() + " dates");
		}

/*
		//lets sort these dates
		List<Date> allFinDates = new ArrayList<Date>(myCode._myFinData.keySet());
		List<Date> sortedFinDates = allFinDates.stream().sorted().collect(Collectors.toList());
		
		// lets print what we read
		Iterator<Date> iter =  sortedFinDates.iterator();
		while(iter.hasNext()) {
			Date finDate = (Date) iter.next();
			if(myCode._myFinData.containsKey(finDate)) {
				DateWiseFinSummary testSummary = (DateWiseFinSummary)(myCode._myFinData.get(finDate));
				testSummary.print(myCode);
				//LOGGER.info(" for date " + finDate + " got total accounts " + testSummary.getAllAccounts().size());;
			}else {
				throw new Exception("logic problem");
			}
		}
*/		
		//write to a new file
		myCode.writeLedgerMain("C:\\ledger_copy\\ledger_2023_transposed.xlsx", "Main");
		LOGGER.info("created the transposed file");
	}
	
	public Date captureFinDate(String cellValue, int cellNum)throws ParseException, Exception {
		//capture the dates
		String dateLabel =  cellValue;
		SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy");
		Date date = formatter.parse(dateLabel.substring(6));//format is "as of 01/01/2010"
				
		if(_columnDateMapping.containsKey(cellNum)) {
			//this is odd
			throw new Exception("date repeating in the header");
		}else {
			_columnDateMapping.put(cellNum, date);
		}
		
		DateWiseFinSummary finSummary =  new DateWiseFinSummary(date, dateLabel);
		_myFinData.put(date, finSummary);
		//LOGGER.error("row 1 and column " + cellNum + " - added date " + date);
		return date;
	}
	
	/*
	public AccountType getAccountType(String cellValue) throws DateWiseFinParsingException{
		AccountType accountType;
		if(cellValue.equalsIgnoreCase("Cash")) accountType = AccountType.Cash;
		else if(cellValue.equalsIgnoreCase("CDs")) accountType =  AccountType.CD;
		else if(cellValue.equalsIgnoreCase("Stocks")) accountType =  AccountType.Stocks;
		else if(cellValue.equalsIgnoreCase("Crypto")) accountType =  AccountType.Crypto;
		else if(cellValue.equalsIgnoreCase("Retirement")) accountType =  AccountType.Retirement;
		else if(cellValue.equalsIgnoreCase("Education")) accountType =  AccountType.Education;
		else if(cellValue.equalsIgnoreCase("HSA")) accountType =  AccountType.HSA;
		else if(cellValue.equalsIgnoreCase("Overseas")) accountType =  AccountType.Overseas;
		else if(cellValue.equalsIgnoreCase("Insurance")) accountType =  AccountType.Insurance;
		else if(cellValue.equalsIgnoreCase("Total")) accountType = AccountType.None;
		else {
			accountType =  AccountType.Unknown;
			throw new DateWiseFinParsingException("this is not possible -  account type - " + cellValue);
		}
		return accountType;
	}
	*/
	
	public void populateHeaderRows(Row firstRow, Row secondRow, Row thirdRow, Row fourthRow) {
		LOGGER.info("inside populateHeaderRows with " + _allAccounts.size());
		if(_allAccounts == null || _allAccounts.isEmpty())
			return;
		
		int cellNum =  0;
		Cell firstRowCell = firstRow.createCell(cellNum);
		firstRowCell.setCellValue("Date");
		
		Cell secondRowCell = secondRow.createCell(cellNum);
		secondRowCell.setCellValue("Date");
		
		Cell thirdRowCell = thirdRow.createCell(cellNum);
		thirdRowCell.setCellValue("Date");
		
		Cell fourthRowCell = fourthRow.createCell(cellNum);
		fourthRowCell.setCellValue("Date");
		cellNum++;

		Iterator<Account> iter = _allAccounts.iterator(); 
		while(iter.hasNext()) {
			Account account = (Account)iter.next();
			_columnAccountMapping.put(cellNum, account);
			//LOGGER.info(" added columnAccountMapping " + account + " column " + cellNum);
			firstRowCell = firstRow.createCell(cellNum);
			firstRowCell.setCellValue(account.getType().toString());
			secondRowCell = secondRow.createCell(cellNum);
			secondRowCell.setCellValue(account.getInstitution());
			thirdRowCell =  thirdRow.createCell(cellNum);
			thirdRowCell.setCellValue(account.getName());
			fourthRowCell =  fourthRow.createCell(cellNum);
			fourthRowCell.setCellValue(account.getOwner());
			cellNum++;
		}
	}
	
	public int addHeaderRows(XSSFSheet sheet) {
		// get the account information and date as headers
		int numRows = 0;
		
		if(_allAccounts != null) {
			//add row1 first account type
			Row firstRow = sheet.createRow(numRows);	
			//add row2 for institution
			numRows++;
			Row secondRow = sheet.createRow(numRows);
			// add row3 for name
			numRows++;
			Row thirdRow = sheet.createRow(numRows);
			numRows++;
			Row fourthRow = sheet.createRow(numRows);
			numRows++;
			populateHeaderRows(firstRow, secondRow, thirdRow, fourthRow);
		}else {
			return 0;
		}
		
		return numRows;
	}
	
	public void writeLedgerMain(String fileName, String sheetName) {
		LOGGER.info("now in writeLedgerMain()");
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet(sheetName);
		
		XSSFCreationHelper helper = workbook.getCreationHelper();
		XSSFCellStyle dateStyle =  workbook.createCellStyle();
		dateStyle.setDataFormat(helper.createDataFormat().getFormat("m/d/yy"));
		
		XSSFCellStyle currencyStyle = workbook.createCellStyle();
		currencyStyle.setDataFormat(helper.createDataFormat().getFormat("$#,##0.00_);[Red]($#,##0.00)"));
		
		XSSFCellStyle currencyStyle2 = workbook.createCellStyle();
		currencyStyle2.setDataFormat(helper.createDataFormat().getFormat("INR ##,##,##0.00_);[Red](INR ##,##,##0.00)"));

		SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");  
		//create first four rows
		int rowNum = addHeaderRows(sheet);
		
		List<Date> allFinDates = new ArrayList<Date>(_myFinData.keySet());
		List<Date> sortedFinDates = allFinDates.stream().sorted().collect(Collectors.toList());
		
		// lets print what we read
		Iterator<Date> iter =  sortedFinDates.iterator();
		while(iter.hasNext()) {
			Date finDate = (Date) iter.next();
			if(_myFinData.containsKey(finDate)) {
				Row row = sheet.createRow(rowNum);
				int cellNum = 0;
				// first cell in a row is date
				Cell cell = row.createCell(cellNum);
				cell.setCellStyle(dateStyle);
				//LOGGER.info("setting date as " + dateFormat.format(finDate));
				cell.setCellValue(dateFormat.format(finDate));
				cellNum++;				
				
				DateWiseFinSummary testSummary = (DateWiseFinSummary)(_myFinData.get(finDate));
				int totalAccounts =  testSummary.getAllAccounts().size();
				LOGGER.error("total accounts for date = " + finDate.toString() + " is " + totalAccounts);
				
				while(cellNum < (_allAccounts.size() +1)) {
					//from column mapping, get the account
					if(_columnAccountMapping.containsKey(cellNum)) {
						Account acct =  _columnAccountMapping.get(cellNum);
						//LOGGER.info(" ********* acct type - " + acct.getType() + "  and " + acct.getCurrency());
						Double acctValue =  testSummary.getAccountValue(acct);
						if(acctValue != null && acctValue != 0.0){
							Cell currencyCell = row.createCell(cellNum);
							if((acct.getCurrency()).equalsIgnoreCase("USD")) {
								currencyCell.setCellStyle(currencyStyle);
							}else if ((acct.getCurrency()).equalsIgnoreCase("INR")) {
								currencyCell.setCellStyle(currencyStyle2);
								//LOGGER.info(" INR type -  curreny value is " + acctValue);
							}else {
								LOGGER.error("unknown currency " + acct.getCurrency());
							}
						
							currencyCell.setCellValue(acctValue);	
						}						
					}else {
						LOGGER.error("Something is missing -  this column - " + cellNum + " is missing a mapping to an account");
					}
					cellNum++;
				}
				
				rowNum++;
			}else {
				LOGGER.error("logic problem");
			}
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
	
	public void readLedgerMain(String fileName, String sheetName) {
		File ledgerFile;
		FileInputStream excelFile;
		XSSFWorkbook workbook = null;
		if(_columnDateMapping != null) {
			_columnDateMapping.clear();
		}
		try {
			ledgerFile = new File(fileName);
			excelFile = new FileInputStream(ledgerFile);
			workbook = new XSSFWorkbook(excelFile);
			XSSFSheet mainSheet = workbook.getSheet(sheetName);
			//LOGGER.error("last row num is " + mainSheet.getLastRowNum() + " first rownum is " + mainSheet.getFirstRowNum() + " last row num is " + mainSheet.getLastRowNum());
			
			for(int rowNum = mainSheet.getFirstRowNum(); rowNum <= mainSheet.getLastRowNum(); rowNum++) { 
				Row row = mainSheet.getRow(rowNum);
				AccountType accountType = null;
				String institution = null;
				String name = null;
				String owner = null;
				
				boolean isThisSummaryRow = false;
				
				for(int cellNum = row.getFirstCellNum(); cellNum <= row.getLastCellNum(); cellNum++) {
					Cell cell = row.getCell(cellNum, Row.MissingCellPolicy.RETURN_NULL_AND_BLANK);
					
					if(cell == null)
						continue;
					
					double value = 0.0;
					String cellValue = null;
					String cellFormula=null;
						
					switch(cell.getCellType()) {
					case NUMERIC:
						//LOGGER.error(cell.getNumericCellValue());
						value = cell.getNumericCellValue();
						break;
					case STRING:
						//LOGGER.error(cell.getStringCellValue());
						// this is the header information either on the column wise or row wise
						cellValue = cell.getStringCellValue();
						break;
					case FORMULA :
						cellFormula = cell.getCellFormula();
						LOGGER.error(" formula  " + cellFormula);
						break;
					case BLANK:
						break;
					case _NONE :
					case BOOLEAN : 
					case ERROR:
						LOGGER.error(" cell type is " + cell.getCellType() + " at rownum " + rowNum + " and column " + cellNum);
						break;
					}

					if(rowNum == mainSheet.getFirstRowNum()) {
						if((cellNum >= row.getFirstCellNum() + 4) && (cellValue != null && cellValue.trim().length() > 0)) {
							captureFinDate(cellValue, cellNum);						
						}else {
							//we can ignore these on the first row
						}
					}else {
						// this is not the first row
						//first column is the account type
						if(cellNum == row.getFirstCellNum()) {
							if(cellValue.equalsIgnoreCase("Total")) {
								// these are not accounts, these are summary values(formulae)
								//TODO:
								
								// for now we will ignore these
								isThisSummaryRow = true;
								break;
							}
							accountType =  AccountType.getAccountType(cellValue);
							
						}else if (cellNum == (row.getFirstCellNum() + 1)) {
							// column2 is institution
							institution = cellValue;
						}else if (cellNum == (row.getFirstCellNum() + 2)) {				
							// column 3 is name
							name = cellValue;
						}else if(cellNum == (row.getFirstCellNum() + 3)) {
							owner =  cellValue;
						}else {
							//these are the values we need to store in DateWiseFinSummary
							Account thisAccount = null;
							String currencyCode = "USD";
							if(accountType == AccountType.Overseas)
								currencyCode = "INR";
							thisAccount = new Account(accountType, institution, name, owner, currencyCode);
							//check if this account is in the standard account metadata
							if(!isThisAccountAStandardOne(thisAccount)) {
								LOGGER.error("this account -  " + thisAccount + " is not a standard one");
							}
							
							if(_columnDateMapping.containsKey(cellNum)) {
								Date tempDate = (Date) _columnDateMapping.get(cellNum);
								if(_myFinData.containsKey(tempDate)) {
									DateWiseFinSummary summary = (DateWiseFinSummary)_myFinData.get(tempDate);
									//LOGGER.error("Date = " + tempDate + " adding account " + thisAccount + " value " + value);
									summary.addData(thisAccount, value);
								}else {
									//this is not possible
									throw new Exception("this is not posslble = logic problem - not able to find a date in DateWiseFinSummary for CellNum" + cellNum);
								}
							}else {
								//System.out.print(" for cell value = " + cellValue);
								//LOGGER.error("this is odd -  column to date mapping missing - logic problem for rowNum " + rowNum + " cellNum " + cellNum);
							}	
						}
					}
				}// end of for(cellNum...
				if(isThisSummaryRow) {
					LOGGER.info("Ignoring summary row " + rowNum);
				}
			}//end of for(rowNum...			
		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			if(workbook != null) {
				try{
					workbook.close();
				}catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	public boolean isThisAccountAStandardOne(Account acct) {
		if(acct == null || _allAccounts == null) return false;

		boolean result = false;
		Iterator<Account> iter =  _allAccounts.iterator();
		while(iter.hasNext()) {
			Account stdAccount = (Account)(iter.next());
			if(stdAccount.equals(acct)) {
				result =  true;
				break;
			}
		}
		return result;
	}
	
	/*
	public void testReadCode(String fileName, String sheetName) {
		File ledgerFile;
		FileInputStream excelFile;
		XSSFWorkbook workbook = null;
		try {
			ledgerFile = new File(fileName);
			excelFile = new FileInputStream(ledgerFile);
			workbook = new XSSFWorkbook(excelFile);
			XSSFSheet mainSheet = workbook.getSheet(sheetName);
			Iterator<Row> rowIterator =  mainSheet.iterator();
			int rowNum = 0;
			while(rowIterator.hasNext()) {
				Row row =  rowIterator.next();
				LOGGER.error(" row #" + rowNum++);
				Iterator<Cell> cellIterator = row.cellIterator();
				while(cellIterator.hasNext()) {
					Cell cell =  cellIterator.next();
				
					switch(cell.getCellType()) {
						case NUMERIC:
							LOGGER.error(cell.getNumericCellValue());
							break;
						case STRING:
							LOGGER.error(cell.getStringCellValue());
							break;
						case _NONE :
						case BOOLEAN : 
						case BLANK:
						case ERROR:
						case FORMULA :
							LOGGER.error(" cell type is " + cell.getCellType());
							break;
					}
				}
			}
			
		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			if(workbook != null) {
				try{
					workbook.close();
				}catch(Exception e) {
					
				}
			}
		}
	}
*/

	/*
	public static void testCode() {
		// Blank workbook 
        XSSFWorkbook workbook = new XSSFWorkbook(); 
  
        // Creating a blank Excel sheet 
        XSSFSheet sheet 
           = workbook.createSheet("student Details"); 
      
        // Creating an empty TreeMap of string and Object][] 
        // type 
        Map<String, Object[]> data 
            = new TreeMap<String, Object[]>(); 
 
        // Writing data to Object[] 
        // using put() method 
        data.put("1", 
                 new Object[] { "ID", "NAME", "LASTNAME" }); 
        data.put("2", 
                 new Object[] { 1, "Pankaj", "Kumar" }); 
        data.put("3", 
                 new Object[] { 2, "Prakashni", "Yadav" }); 
        data.put("4", new Object[] { 3, "Ayan", "Mondal" }); 
        data.put("5", new Object[] { 4, "Virat", "kohli" }); 
  
        // Iterating over data and writing it to sheet 
        Set<String> keyset = data.keySet(); 
  
        int rownum = 0; 
  
        for (String key : keyset) { 
  
            // Creating a new row in the sheet 
            Row row = sheet.createRow(rownum++);
            Object[] objArr = data.get(key); 
            
            int cellnum = 0;
  
            for (Object obj : objArr) { 
  
                // This line creates a cell in the next 
                //  column of that row 
                Cell cell = row.createCell(cellnum++); 
  
                if (obj instanceof String) 
                    cell.setCellValue((String)obj); 
  
                else if (obj instanceof Integer) 
                    cell.setCellValue((Integer)obj); 
            } 
        } 
  
        // Try block to check for exceptions 
        try { 
  
            // Writing the workbook 
            FileOutputStream out = new FileOutputStream( 
                new File("gfgcontribute.xlsx")); 
            workbook.write(out); 
  
            // Closing file output connections 
            out.close(); 
            
            workbook.close();
  
            // Console message for successful execution of 
            // program 
            LOGGER.error( 
                    "gfgcontribute.xlsx written successfully on disk."); 
            } 
      
            // Catch block to handle exceptions 
            catch (Exception e) { 
      
                // Display exceptions along with line number 
                // using printStackTrace() method 
                e.printStackTrace(); 
            } 		
	}
	*/
}
