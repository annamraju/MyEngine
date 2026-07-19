package org.kumar.dataload;

import java.util.stream.Stream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.nio.file.StandardCopyOption;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.util.BofaCsvToObjects;
import org.kumar.dataload.util.DataLoadJsonStore;
import org.kumar.dataload.util.DataloadBaseClass;
import org.kumar.dataload.util.BofaCsvToObjects.Txn;
import org.kumar.excel.util.DateInterval;
import org.kumar.excel.util.LedgerRecord;

public class BofaCCLoad extends DataloadBaseClass{

	private static final Logger LOGGER = LogManager.getLogger("BofaCCLoad");
	
	protected final String accountName="BOA CC";
	public final static String SOURCE_FOLDER =  "D:\\fin_docs\\BofaCC";
	public final static String transactionExt = ".csv";
	public final static String PROCESSED_FOLDER =  "D:\\fin_docs\\BofaCC\\Processed";
	public static short _cycleStart = 26;
	public static short _cycleEnd = 25;	
	
	/**
	 * @override
	 */
	public String getAccountName() {
		return accountName;
	}
	
	/**
	 * @override
	 * @return
	 */
	public Map<DateInterval, Integer> buildCycleData(){
		Iterator<LedgerRecord> iter = _records.iterator();
		HashMap<DateInterval, Integer> loadedData = new HashMap<DateInterval, Integer>();
		while(iter.hasNext()) {
			LedgerRecord rec =  iter.next();
			if(rec.getAccount().equalsIgnoreCase(accountName)) {
				DateInterval cycle = DateInterval.getDateInterval(rec.getTransDate(), _cycleStart, _cycleEnd);
				if(loadedData.containsKey(cycle)) {
					Integer count = loadedData.get(cycle);
					count = count + 1;
					loadedData.put(cycle, count);
				}else {
					loadedData.put(cycle, 1);
				}
			}
		}
		return loadedData;		
	}
	
	/**
	 * @override
	 * @param loadedData
	 * @return
	 */
	public List<DateInterval> findMissingCycles(Map<DateInterval, Integer> loadedData){
		List<DateInterval> missingcycles = DateInterval.findMissingCycles(loadedData.keySet(), _cycleStart, _cycleEnd);	
		//these are the cycles for which there was no data from the ledger
		// however there might not be any data at all
		//for each of the cycles check to see in the json file that its marked as completed with 0 records
		LOGGER.error(" before pruning - # of missing records is " + missingcycles.size());
		List<DateInterval> finalMissing =  new ArrayList<DateInterval>();
		for(int i = 0; i < missingcycles.size(); i++  ) {
			DateInterval di = missingcycles.get(i);
			try {
				DataLoadJsonStore.LoadInterval li =  _store.findIntervalOrThrow(accountName, di.getStartDate(), di.getEndDate());
				//LOGGER.error("for " + accountName + " and " + di.getStartDate()+ " and " + di.getEndDate() + "got " + li.numberOfRecords + " and " + li.status);
				if(li.numberOfRecords == 0 && li.status.trim().equalsIgnoreCase("Loaded")) {
					// then its marked completed
					//LOGGER.error("removing index " + i + "with " + di.getStartDate() + " and " + di.getEndDate() );
					//missingcycles.remove(i);
				}else {
					LOGGER.error("**** THIS CANNOT happen - cycle missing, but number of records is not 0 or status not Loaded");
				}
			}catch(IllegalStateException e1) {
				// its not there, so truly missing
				finalMissing.add(di);
			}
		}
		
		return finalMissing;		
	}
	
	/**
	 * @override
	 * @param loadedData
	 */
	public void syncDataLoadJson(Map<DateInterval, Integer> loadedData) {
		//load the data load JSON
		loadDataLoadJson();
		
		//first add an account if that does not exist
		try {
			_store.findAccountOrThrow(accountName);
		}catch(IllegalStateException e1 ) {
			_store.upsertAccount(accountName);
		}
		
		for (Map.Entry<DateInterval, Integer> entry : loadedData.entrySet()) {
            DateInterval di = entry.getKey();
            Integer recordCount = entry.getValue();

            try {
                //String start = di.getStartDate().format(formatter);
                //String end = di.getEndDate().format(formatter);                
                _store.findIntervalOrThrow(accountName, di.getStartDate(), di.getEndDate());
            }catch(IllegalStateException e2) {
            	// this is when the account and interval combination is not found
            	_store.upsertInterval(accountName,
						di.getStartDate(),
						di.getEndDate(),
                        "Loaded",
                        recordCount);
            }catch(IllegalArgumentException e) {
            	e.printStackTrace();
            	LOGGER.error(" start date is " + di.getStartDate());
            	LOGGER.error(" end date is " + di.getEndDate());
            }
        }
		
		// save the updates
		saveDataLoadJson();						
	}
	
	/**
	 * Add Bofa transactions to an existing ledger
	 * @param txns
	 * @param counter
	 */
	public void addToLedger(Txn txns, short counter) {
		_records.add(txns.covnertToLedgerRecord(counter));
	}
	
	/**
	 * 
	 * @param txns
	 * @return
	 */
	public Map<DateInterval, Integer> extractCycleIntervals(Txn[] txns){
		Map<DateInterval, Integer> cycles = new HashMap<DateInterval, Integer>();
		for(int i = 0; i < txns.length; i++) {
			DateInterval cycle =  DateInterval.getDateInterval((txns[i]).getPostedDate(), _cycleStart, _cycleEnd);
			if(cycles.containsKey(cycle)) {
				// we already got it - nothing to do
			}else {
				cycles.put(cycle, 1);
			}
		}
		return cycles;
	}

	/** Group transactions by billing cycle (one statement export can span multiple cycles). */
	private Map<DateInterval, List<Txn>> groupTransactionsByInterval(Txn[] txns) {
		Map<DateInterval, List<Txn>> byInterval = new HashMap<>();
		for (Txn txn : txns) {
			DateInterval cycle = DateInterval.getDateInterval(txn.getPostedDate(), _cycleStart, _cycleEnd);
			byInterval.computeIfAbsent(cycle, k -> new ArrayList<>()).add(txn);
		}
		return byInterval;
	}
	
	/**
	 * This is to read a transaction file for Bofa given the file name if not loaded as per dataload json file 
	 * and then update the data load status in the json
	 * @param csvPath
	 */
	public void processTransactionFile(Path csvPath) {
		LOGGER.info(" reading " + csvPath);
		try {
			Txn[] txns = BofaCsvToObjects.readTxns(csvPath);
			// One file may contain multiple cycles -> group, then load each unloaded cycle once
			Map<DateInterval, List<Txn>> txnsByInterval = groupTransactionsByInterval(txns);

			boolean loadedAny = false;
			for (Map.Entry<DateInterval, List<Txn>> entry : txnsByInterval.entrySet()) {
			    DateInterval interval = entry.getKey();
			    List<Txn> intervalTxns = entry.getValue();
			    try {
					 _store.findIntervalOrThrow(accountName,interval.getStartDate(), interval.getEndDate()	);
					// that means this is already loaded
			    }catch(java.lang.IllegalStateException e1) {
					for(int i = 0; i < intervalTxns.size(); i++) {
						addToLedger(intervalTxns.get(i), (short)(i+1));  //lets start the seq number in ledger with 1
					}
					
					LOGGER.info("updating dataload.json with a new interva " + interval.getStartDate()
							+ "  and " + interval.getEndDate()
							+ " (" + intervalTxns.size() + " of " + txns.length + " transactions)");
					_store.upsertInterval(accountName,
							interval.getStartDate(),
							interval.getEndDate(),
                            "Loaded",
                            intervalTxns.size());
					loadedAny = true;
			    }
			}

			// Move once after all cycles are handled (never per-cycle)
			if (loadedAny) {
				Path targetDir =  Path.of(PROCESSED_FOLDER);					
				Files.createDirectories(targetDir); // make sure it exists
				Path target = targetDir.resolve(csvPath.getFileName());
				LOGGER.info("moving " + csvPath + " to " + target);
				Files.move(csvPath, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Main
	 * @param args
	 * @throws  
	 */
	public static void main(String[] args) {		
		//read the ledger file
		BofaCCLoad process = new BofaCCLoad();
		//String ledgerFile =  "D:\\fin_docs\\Consolidated Ledger v2_mar2025.xlsx";
		String ledgerFile =  "D:\\fin_docs\\updated_ledger_01132026_latest.xlsx";
		String sheetName =  "Runbook";
		String outputFile =  "D:\\fin_docs\\updated_ledger_01172026.xlsx";
		
		// read ledger
		try {
			process.readLedgerFile(ledgerFile, sheetName);		
		}catch(Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		LOGGER.info("done reading the ledger");		

		//load the dataloadJson
		process.loadDataLoadJson();
		
		// now read the BOF CC transaction files and add them to the ledger
		int beforeCount = process.getLedgerRecordCount();		

		// now process all the files in that source folder
		Path folderPath = Paths.get(BofaCCLoad.SOURCE_FOLDER);
		try (Stream<Path> paths = Files.list(folderPath)) {
            paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().toLowerCase().endsWith(transactionExt))
                .forEach(process::processTransactionFile);

        } catch (IOException e) {
            e.printStackTrace();
        }		

		// get the after count
		int afterCount = process.getLedgerRecordCount();
		
		if(afterCount != beforeCount) {
			//finally write ledger records to a file
			try {
				LOGGER.info("saving the new ledger " + outputFile);
				process.saveExcelAndJson(Path.of(outputFile));
			}catch(Exception e1) {
				e1.printStackTrace();
			}
		}else {
			LOGGER.info("No changes- nothing to save");
		}
	}
}
