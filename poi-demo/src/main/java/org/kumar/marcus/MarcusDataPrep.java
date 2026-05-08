package org.kumar.marcus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
//import java.util.Iterator;
import java.util.ListIterator;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFCreationHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFRow;

import java.util.ArrayList;

public class MarcusDataPrep {
	
	private ArrayList<MarcusTransactionRecord> records;
	
	private static final Logger LOGGER = LogManager.getLogger(MarcusDataPrep.class);
	//private static final Logger LOGGER = LogManager.getRootLogger();

	public MarcusDataPrep() {
		records =  new ArrayList<MarcusTransactionRecord>();
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		MarcusDataPrep myObj = new MarcusDataPrep();
		XSSFWorkbook workbook =  null;
		
		LOGGER.info("starting");
		try {
			//workbook = myObj.readFile("C:\\Users\\annam\\Downloads\\Marcus_01012024_08312024_v2.xlsx");
			workbook = myObj.readFileSingleColumnFile("C:\\Users\\annam\\Downloads\\Marcus_single_column.xlsx");
			//workbook = myObj.readMergedFile("C:\\Users\\annam\\Downloads\\Marcus_01012024_08312024.xlsx");
			myObj.saveRecords("C:\\\\Users\\\\annam\\\\Downloads\\\\Marcus_trans_0823.xlsx");
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

	public void saveRecords(String fileName) {
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet("MarcusTransactions");
		
		XSSFCreationHelper helper = workbook.getCreationHelper();
		XSSFCellStyle dateStyle =  workbook.createCellStyle();
		dateStyle.setDataFormat(helper.createDataFormat().getFormat("m/d/yy"));
		
		XSSFCellStyle currencyStyle = workbook.createCellStyle();
		currencyStyle.setDataFormat(helper.createDataFormat().getFormat("$#,##0.00_);[Red]($#,##0.00)"));	
		
		//create a header row
		XSSFRow headerRow = sheet.createRow(0);
		XSSFCell headerCell1 = headerRow.createCell(0);
		headerCell1.setCellValue("Acct Type");
		XSSFCell headerCell2 = headerRow.createCell(1);
		headerCell2.setCellValue("Order");
		XSSFCell headerCell3 = headerRow.createCell(2);
		headerCell3.setCellValue("Year");
		XSSFCell headerCell4 = headerRow.createCell(3);
		headerCell4.setCellValue("Month");
		XSSFCell headerCell5 = headerRow.createCell(4);
		headerCell5.setCellValue("Date");
		XSSFCell headerCell6 = headerRow.createCell(5);
		headerCell6.setCellValue("Desc");
		XSSFCell headerCell7 = headerRow.createCell(6);
		headerCell7.setCellValue("Amt");
		XSSFCell headerCell8 = headerRow.createCell(7);
		headerCell8.setCellValue("Balance");
		
		SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");
		
		ListIterator<MarcusTransactionRecord> iter =  records.listIterator(records.size());
		int rowNum = 0;
		while(iter.hasPrevious()){
			MarcusTransactionRecord rec = iter.previous();
			//LOGGER.info("dumping - " + rec);
			XSSFRow row = sheet.createRow(rowNum+1);
			
			// first cell
			XSSFCell cell1 =  row.createCell(0);
			cell1.setCellValue("Marcus Savings");
			
			//second cell
			XSSFCell cell2 =  row.createCell(1);
			cell2.setCellValue(rowNum+1);
			
			//third cell
			XSSFCell cell3 =  row.createCell(2, CellType.FORMULA);
			int temp = rowNum+2;
			cell3.setCellFormula("YEAR(E" + temp + ")");
			
			//fourth cell
			XSSFCell cell4 = row.createCell(3, CellType.FORMULA);
			cell4.setCellFormula("TEXT(E" + temp + ", \"mmmm\")");
			
			//fifth cell
			XSSFCell cell5 =  row.createCell(4);
			cell5.setCellStyle(dateStyle);
			LOGGER.info("setting date as - " + formatter.format(rec.getDate()));
			cell5.setCellValue(rec.getDate());
			
			//sixth cell
			XSSFCell cell6 =  row.createCell(5);
			cell6.setCellValue(rec.getDescription());
			
			//seventh cell
			XSSFCell cell7 =  row.createCell(6);
			cell7.setCellStyle(currencyStyle);
			cell7.setCellValueImpl(rec.getAmt());
			
			//eight cell
			XSSFCell cell8 =  row.createCell(7);
			cell8.setCellStyle(currencyStyle);
			cell8.setCellValueImpl(rec.getBalance());
			
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
	
	public XSSFWorkbook readFileSingleColumnFile(String transactionFile){
		File ledgerFile;
		FileInputStream excelFile;
		XSSFWorkbook workbook = null;
		SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yy");
		
		//create a cache
		MyArrayStack<String> myCacheArrayStack = new MyArrayStack<String>(10);

		try {
			ledgerFile = new File(transactionFile);
			excelFile = new FileInputStream(ledgerFile);
			workbook = new XSSFWorkbook(excelFile);
			XSSFSheet mainSheet = workbook.getSheet("Sheet1");			
			int rowNum = mainSheet.getFirstRowNum();
			int recCount = 0;
			DataFormatter cellFormatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            
			while(rowNum < mainSheet.getLastRowNum()) {
				LOGGER.info("reading " + rowNum);
				XSSFRow row = mainSheet.getRow(rowNum);
				XSSFCell cell1 = row.getCell(0);
				rowNum++;
				if(cell1 == null) continue;
				
				String val = cellFormatter.formatCellValue(cell1, evaluator);

				if(val.equalsIgnoreCase("Track my transfer")) continue;
				if(val.equalsIgnoreCase("|")) continue;
				
				myCacheArrayStack.push(val);
				
				if(val.contains("Balance")) {
					//this is the logical end of a Marcus Transaction
					//now start popping out of stack to b
					String balStr =  myCacheArrayStack.pop();
					String amtStr =  myCacheArrayStack.pop();
					String amtType = myCacheArrayStack.pop(); // this will be either Deposit or Withdrawals or Income
					String dateStr =  myCacheArrayStack.pop();
					String desc =  myCacheArrayStack.pop();
					
					double bal =  Double.parseDouble(balStr.replace("Balance: $","").replaceAll(",", ""));
					double amt =  0.0;
					if(amtType.equalsIgnoreCase("Deposit") || amtType.equalsIgnoreCase("Income"))
						amt  = Double.parseDouble(amtStr.replace("$", "").replace(",",""));
					else if (amtType.equalsIgnoreCase("Withdrawals"))
						amt  = -1 * (Double.parseDouble(amtStr.replace("$", "").replace(",","")));
					else
						throw(new Exception("incorrect logic"));
					Date dt =  formatter.parse(dateStr);
						
					MarcusTransactionRecord marcusRec = new MarcusTransactionRecord(dt, desc, amt, bal);
					if(records == null)
						records = new ArrayList<MarcusTransactionRecord>();
					records.add(marcusRec);
					recCount++;
				}
			} // end of while
			LOGGER.info("read a total of " + recCount + "records ");
			 
		}catch(Exception e) {
			e.printStackTrace();
		}finally {
			if(workbook != null) { 
				try{
					workbook.close();
				}catch(Exception e1) {
					
				}
			}
		}
		
		return workbook;
	}
	
	public XSSFWorkbook readFile(String transactionFile) {
		File ledgerFile;
		FileInputStream excelFile;
		XSSFWorkbook workbook = null;
		//SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

		try {
			ledgerFile = new File(transactionFile);
			excelFile = new FileInputStream(ledgerFile);
			workbook = new XSSFWorkbook(excelFile);
			XSSFSheet mainSheet = workbook.getSheet("Sheet1");			
			int rowNum = mainSheet.getFirstRowNum()+1;
			while(rowNum <= mainSheet.getLastRowNum()) {
				XSSFRow row = mainSheet.getRow(rowNum);
				XSSFRow row2 = mainSheet.getRow(rowNum+1);
				
				LOGGER.info("reading row " + rowNum);
				XSSFCell cell1 = row.getCell(0);
				XSSFCell cellDesc = row.getCell(1);
				XSSFCell amt = row.getCell(2);

				//get the date
				Calendar date = Calendar.getInstance();
				date.setTime(cell1.getDateCellValue());

				double value = amt.getNumericCellValue();

				XSSFCell row2Cell2 = row2.getCell(2);
				String[] values = row2Cell2.getStringCellValue().split(":");	
				double bal =  Double.parseDouble(convertCurrencyStringToDoubleString(values[1].trim()));
				
				MarcusTransactionRecord rec = new MarcusTransactionRecord(date.getTime(), cellDesc.getStringCellValue(), value, bal);
				//LOGGER.info("trans1 - " + rec.toString());
				rowNum = rowNum+2;
				records.add(rec);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}		
	return workbook;
	}
	
	public XSSFWorkbook readMergedFile(String transactionFile) {
		File ledgerFile;
		FileInputStream excelFile;
		XSSFWorkbook workbook = null;
		//SimpleDateFormat formatter = new SimpleDateFormat("dd-MM-yyyy");

		try {	
			ledgerFile = new File(transactionFile);
			excelFile = new FileInputStream(ledgerFile);
			workbook = new XSSFWorkbook(excelFile);
			XSSFSheet mainSheet = workbook.getSheet("Sheet1");			
			int rowNum = mainSheet.getFirstRowNum();
			while(rowNum <= mainSheet.getLastRowNum()) {
				
				XSSFRow row = mainSheet.getRow(rowNum);
				XSSFRow row2 = mainSheet.getRow(rowNum+1);
				
				LOGGER.info("reading row " + rowNum);
				XSSFCell cell1 = row.getCell(0);
				XSSFCell cellDesc = row.getCell(1);
				XSSFCell amt = row.getCell(2);

				//get the date
				Calendar date = Calendar.getInstance();
				date.setTime(cell1.getDateCellValue());

				double value = amt.getNumericCellValue();

				XSSFCell row2Cell2 = row2.getCell(2);
				String[] values = row2Cell2.getStringCellValue().split(":");	
				double bal =  Double.parseDouble(convertCurrencyStringToDoubleString(values[1].trim()));
				
				MarcusTransactionRecord rec = new MarcusTransactionRecord(date.getTime(), cellDesc.getStringCellValue(), value, bal);
				LOGGER.info("trans1 - " + rec.toString());
				
				records.add(rec);
				rowNum = rowNum+2;
			}
		}catch(Exception e) {
			e.printStackTrace();
		}		
	return workbook;
	}
	public String convertCurrencyStringToDoubleString(String input) {
		
		if(input == null || input.trim().length()== 0) return input;
		int index =  0;
		StringBuffer str = new StringBuffer(input.length());
		while(index < input.length()) {
			if(	input.charAt(index) == '$' || 
				input.charAt(index) == ' ' || 
				input.charAt(index) == ',' ) {
				index++;
				continue;
			}
			str.append(input.charAt(index));
			index++;
		}
		return str.toString();
	}
}
