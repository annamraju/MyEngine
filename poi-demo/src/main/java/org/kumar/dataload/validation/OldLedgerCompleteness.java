/**
 * 
 */
package org.kumar.dataload.validation;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.ChaseCCAnantha;
import org.kumar.dataload.ChaseCCLoad;
import org.kumar.dataload.MonthlyLoad;
import org.kumar.dataload.util.BOACheckingRecord;
import org.kumar.dataload.util.ChaseCheckingRecord;
import org.kumar.excel.util.DateInterval;
import org.kumar.dataload.util.CitiSavingsRecord;
import org.kumar.dataload.util.EtradeBankRecord;
import org.kumar.dataload.util.GTEFCUCheckingRecord;
import org.kumar.dataload.util.HSARecord;
import org.kumar.dataload.util.MarcusSavingsRecord;
import org.kumar.dataload.util.BofaCCRecord;

public class OldLedgerCompleteness{
	private static final Logger LOGGER = LogManager.getLogger("LedgerCompleteness");

	public void checkMacusSavingsCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "Marcus Savings";
        String srcfolder = "D:\\fin_docs\\Marcus Savings";
        String ext = ".pdf";
        String processedFolder = "D:\\fin_docs\\Marcus Savings\\Processed";
        MonthlyLoad<MarcusSavingsRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                MarcusSavingsRecord::readTransactionsFromPdf,                              // reader
                MarcusSavingsRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ...");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		if(missingcycles == null) LOGGER.info(" None");
		else LOGGER.info(missingcycles);			
	}

	public void checkHSACompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "HSA";
        String srcfolder = "D:\\fin_docs\\HSA";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\HSA\\Processed";
        MonthlyLoad<HSARecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                HSARecord::readTransactionsFromCsv,                              // reader
                HSARecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkGTEFCUCCCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "GTEFCU CC";
        String srcfolder = "D:\\fin_docs\\GTEFCU CC";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\GTEFCU CC\\Processed";
        MonthlyLoad<GTEFCUCheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                GTEFCUCheckingRecord::readTransactionsFromCsv,                              // reader
                GTEFCUCheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkGTEFCUCDCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "GTEFCU CD";
        String srcfolder = "D:\\fin_docs\\GTEFCU CD";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\GTEFCU CD\\Processed";
        MonthlyLoad<GTEFCUCheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                GTEFCUCheckingRecord::readTransactionsFromCsv,                              // reader
                GTEFCUCheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkGTEFCUSavingsCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "GTEFCU Savings";
        String srcfolder = "D:\\fin_docs\\GTEFCU Savings";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\GTEFCU Savings\\Processed";
        MonthlyLoad<GTEFCUCheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                GTEFCUCheckingRecord::readTransactionsFromCsv,                              // reader
                GTEFCUCheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkGTEFCUCheckingCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "GTEFCU Checking";
        String srcfolder = "D:\\fin_docs\\GTEFCU Checking";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\GTEFCU Checking\\Processed";
        MonthlyLoad<GTEFCUCheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                GTEFCUCheckingRecord::readTransactionsFromCsv,                              // reader
                GTEFCUCheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkEtradeBankCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "etrade Bank";
        String srcfolder = "D:\\fin_docs\\etrade Bank";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\etrade Bank\\Processed";
        MonthlyLoad<EtradeBankRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                EtradeBankRecord::readTransactionsFromCsv,                              // reader
                EtradeBankRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkCitiSavingsCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "Citi Savings";
        String srcfolder = "D:\\fin_docs\\Citi Savings";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\Citi Savings\\Processed";
        MonthlyLoad<CitiSavingsRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                CitiSavingsRecord::readTransactionsFromCsv,                              // reader
                CitiSavingsRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkChaseCheckingAnanthaCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "Chase Checking (Anantha LLC)";
        String srcfolder = "D:\\fin_docs\\Chase Checking (Anantha LLC)";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\Chase Checking (Anantha LLC)\\Processed";
        MonthlyLoad<ChaseCheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                ChaseCheckingRecord::readTransactionsFromCsv,                              // reader
                ChaseCheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkChaseCheckingCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "Chase Checking";
        String srcfolder = "D:\\fin_docs\\Chase Checking";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\Chase Checking\\Processed";
        MonthlyLoad<ChaseCheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                ChaseCheckingRecord::readTransactionsFromCsv,                              // reader
                ChaseCheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}

	public void checkBofaSavingCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "BOA Savings";
        String srcfolder = "D:\\fin_docs\\BOA Savings";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\BOA Savings\\Processed";
        MonthlyLoad<BOACheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                BOACheckingRecord::readTransactionsFromCsv,                              // reader
                BOACheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}
	
	public void checkBofaCheckingAnjaliCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "BOA Checking(Anjali)";
        String srcfolder = "D:\\fin_docs\\BOA Checking(Anjali)";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\BOA Checking(Anjali)\\Processed";
        MonthlyLoad<BOACheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                BOACheckingRecord::readTransactionsFromCsv,                              // reader
                BOACheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}
	
	public void checkBofaCheckingAnanyaCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "BOA Checking(Ananya)";
        String srcfolder = "D:\\fin_docs\\BOA Checking(Ananya)";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\BOA Checking(Ananya)\\Processed";
        MonthlyLoad<BOACheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                BOACheckingRecord::readTransactionsFromCsv,                              // reader
                BOACheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}
	
	public void checkBofaCheckingCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException{
        String account = "BOA Checking";
        String srcfolder = "D:\\fin_docs\\BOA Checking";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\BOA Checking\\Processed";
        MonthlyLoad<BOACheckingRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                BOACheckingRecord::readTransactionsFromCsv,                              // reader
                BOACheckingRecord::getTransactionDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        // read ledger
        process.readLedgerFile(ledgerFile, sheetName);		
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);			
	}
	
	public void checkBofaCCCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException {
        String account = "BOA CC";
        String srcfolder = "D:\\fin_docs\\BOA CC";
        String ext = ".csv";
        String processedFolder = "D:\\fin_docs\\BOA CC\\Processed";
        MonthlyLoad<BofaCCRecord> process =
            new MonthlyLoad<>(
                account,
                srcfolder,
                ext,
                processedFolder,
                BofaCCRecord::readTransactionsFromCsv,                              // reader
                BofaCCRecord::getPostedDate,                // date getter
                (rec, order) -> rec.convertToLedgerRecord(account, order) // convert
            );

        //read the ledger file
        process.readLedgerFile(ledgerFile, sheetName);
		LOGGER.info("read the ledger file " + ledgerFile + " for " + process.getAccountName());
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("built the cycles for " + process.getAccountName() );
		process.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + process.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = process.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);	
	}

	public void checkChaseCCAnanthaCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException {
		ChaseCCAnantha accountUtil =  new ChaseCCAnantha();
		//read the ledger file
		accountUtil.readLedgerFile(ledgerFile, sheetName);
		LOGGER.info("read the ledger file " + ledgerFile + " for " + accountUtil.getAccountName());
		Map<DateInterval, Integer> loadedData = accountUtil.buildCycleData();
		LOGGER.info("built the cycles for " + accountUtil.getAccountName() );
		accountUtil.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + accountUtil.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = accountUtil.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);	
	}
	
	public void checkChaseCCCompleteness(String ledgerFile, String sheetName) throws FileNotFoundException {
		ChaseCCLoad accountUtil =  new ChaseCCLoad();
		//read the ledger file
		accountUtil.readLedgerFile(ledgerFile, sheetName);
		LOGGER.info("read the ledger file " + ledgerFile + " for " + accountUtil.getAccountName());
		Map<DateInterval, Integer> loadedData = accountUtil.buildCycleData();
		LOGGER.info("built the cycles for " + accountUtil.getAccountName() );
		accountUtil.syncDataLoadJson(loadedData);
		LOGGER.info("sync'ed the json file for " + accountUtil.getAccountName() );
		LOGGER.info("Missing intervals are ");
		List<DateInterval> missingcycles = accountUtil.findMissingCycles(loadedData);
		LOGGER.info(missingcycles);	
	}

	/**Main
	 * @param args
	 */
	public static void main(String[] args) throws InterruptedException{
		//first read the file
		OldLedgerCompleteness process = new OldLedgerCompleteness();
		//String ledgerFile =  "D:\\fin_docs\\Consolidated Ledger v2_mar2025.xlsx";
		String ledgerFile =  "D:\\fin_docs\\updated_ledger_01192026_almost_final.xlsx";
		String sheetName =  "Runbook";

		// now do the completeness check for each account
		try {
			process.checkChaseCCAnanthaCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		Thread.sleep(5000);
		
		// now do the completeness check for each account
		try {
			process.checkBofaCCCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}

		Thread.sleep(5000);
		
		// now do the completeness check for each account
		try {
			process.checkChaseCCCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		// completenes check for Bofa checking
		try {
			process.checkBofaCheckingCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}

		// completeness check BOA Checking(Ananya)
		try {
			process.checkBofaCheckingAnanyaCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}

		// completeness check BOA Checking(Anjali)
		try {
			process.checkBofaCheckingAnjaliCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		// completeness check BOA Savings
		try {
			process.checkBofaSavingCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}

		// completeness check Chase Checking
		try {
			process.checkChaseCheckingCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}

		// completeness check Chase Checking
		try {
			process.checkChaseCheckingAnanthaCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		// completeness check citi savings
		try {
			process.checkCitiSavingsCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		// completeness check etrade bank
		try {
			process.checkEtradeBankCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		// completeness check GTEFCU bank
		try {
			process.checkGTEFCUCheckingCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		// completeness check GTEFCU savings bank
		try {
			process.checkGTEFCUSavingsCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		// completeness check GTEFCU savings bank
		try {
			process.checkGTEFCUCDCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		// completeness check GTEFCU CC
		try {
			process.checkGTEFCUCCCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}

		// completeness check Marcus
		try {
			process.checkMacusSavingsCompleteness(ledgerFile, sheetName);
		}catch(Exception e) {
			e.printStackTrace();
		}

		
	}
}
