package org.kumar.excel;

import java.io.File;
import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.kumar.excel.util.AccountType;
import org.kumar.excel.util.Account;
import org.apache.poi.sl.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class MyTestexcelReadWriteTest {
    private MyTestexcelReadWrite processor;

    @BeforeEach
    void setUp() {
        processor = new MyTestexcelReadWrite();
    }

    @Test
    void testCaptureFinDate_validDate() {
        String header = "as of 12/31/2021";
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
        int cellNum =  2;
        Date result =  null;
        try {
        result =  	processor.captureFinDate(header, cellNum);
        assertNotNull(result);
        }catch(ParseException e) {
        	
        }catch(Exception e2){
        	
        }
        assertEquals("12/31/2021", sdf.format(result));
    }

    @Test
    void testCaptureFinDate_invalidFormat() {
        String header = "Date Unknown";
        int cellNum = 2;
        assertThrows(Exception.class, () -> processor.captureFinDate(header, cellNum));
    }

    @Test
    void testIsThisAccountAStandardOne() {
        processor.loadAllAccounts();

        Account acctTrue = new Account(AccountType.Cash, "GTE fcu","Checking", "Kumar & Padma", "USD");
        Account acctFalse = new Account(AccountType.Cash, "XYZ", "ABC", "Kumar");
        assertTrue(processor.isThisAccountAStandardOne(acctTrue));
        assertFalse(processor.isThisAccountAStandardOne(acctFalse));
    }

    /*
    @Test
    void testPopulateHeaderRows() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Test");
        Row row0 = sheet.createRow(0);
        Row row1 = sheet.createRow(1);
        Row row2 = sheet.createRow(2);
        Row row3 = sheet.createRow(3);

        row0.createCell(4).setCellValue("Asset");
        row1.createCell(4).setCellValue("Bank");
        row2.createCell(4).setCellValue("Checking");
        row3.createCell(4).setCellValue("John");

        processor.loadAllAccounts();

        processor.populateHeaderRows(row0, row1, row2, row3);

        Map<Integer, MyAccountMetadata> metadataMap = processor._colAcctMetaMap;
        assertEquals(1, metadataMap.size());

        MyAccountMetadata meta = metadataMap.get(4);
        assertNotNull(meta);
        assertEquals("Asset", meta._type);
        assertEquals("Bank", meta._institution);
        assertEquals("Checking", meta._name);
        assertEquals("John", meta._owner);
    }

    @Test
    void testWriteLedgerMain_basic() throws Exception {
        // Create dummy data
        processor._myFinData = new TreeMap<>();
        Map<Integer, Double> sampleRow = new HashMap<>();
        sampleRow.put(4, 1000.0);
        processor._myFinData.put(new SimpleDateFormat("MM/dd/yyyy").parse("01/01/2022"), sampleRow);

        MyAccountMetadata meta = new MyAccountMetadata();
        meta._name = "Checking";
        meta._type = "Asset";
        meta._institution = "Bank";
        meta._owner = "John";

        processor._colAcctMetaMap = new HashMap<>();
        processor._colAcctMetaMap.put(4, meta);

        String filePath = "test_output.xlsx";
        processor.writeLedgerMain(filePath, "TestSheet");

        File outFile = new File(filePath);
        assertTrue(outFile.exists());
        outFile.delete(); // cleanup
    }
    */
}

