package org.kumar.dataload;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.util.ChaseCCCsvReader;
import org.kumar.dataload.util.ChaseCCCsvRecord;
import org.kumar.dataload.util.DataLoadJsonStore;
import org.kumar.dataload.util.DataloadBaseClass;
import org.kumar.excel.util.DateInterval;
import org.kumar.excel.util.LedgerRecord;

public class ChaseCCLoad extends DataloadBaseClass {
	private static final Logger LOGGER = LogManager.getLogger("ChaseCC");
	
	protected final String accountName="Chase CC";
	public final static String SOURCE_FOLDER =  "D:\\fin_docs\\ChaseCC";
	public final static String transactionExt = ".csv" ;
	
	public final static String PROCESSED_FOLDER =  "D:\\fin_docs\\ChaseCC\\Processed";
	public static short _cycleStart = 15;
	public static short _cycleEnd = 14;
		
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
	 * @override
	 * @param loadedData
	 * @return
	 */
	public List<DateInterval> findMissingCycles(Map<DateInterval, Integer> loadedData){
		List<DateInterval> missingcycles = DateInterval.findMissingCycles(loadedData.keySet(), _cycleStart, _cycleEnd);
		//these are the cycles for which there was no data from the ledger
		// however there might not be any data at all
		//for each of the cycles check to see in the json file that its marked as completed with 0 records
		List<DateInterval> finalMissing = new ArrayList<DateInterval>();
		for(int i = 0; i < missingcycles.size(); i++  ) {
			DateInterval di = missingcycles.get(i);
			try {
				DataLoadJsonStore.LoadInterval li =  _store.findIntervalOrThrow(accountName, di.getStartDate(), di.getEndDate());
				if(li.numberOfRecords == 0 && li.status.equalsIgnoreCase("Loaded")) {
					// then its marked completed
					missingcycles.remove(i);
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
	 * This is to extract the cycles
	 * @param txns
	 * @return
	 */
	public Map<DateInterval, Integer> extractCycleIntervals(List<ChaseCCCsvRecord> txns){
		Map<DateInterval, Integer> cycles = new HashMap<DateInterval, Integer>();
		for(int i = 0; i < txns.size(); i++) {
			DateInterval cycle =  DateInterval.getDateInterval((txns.get(i)).getTransactionDate(), _cycleStart, _cycleEnd);
			if(cycles.containsKey(cycle)) {
				// we already got it - nothing to do
			}else {
				cycles.put(cycle, 1);
			}
		}
		return cycles;
	}
	
	/**
	 * @override
	 * This is to read a transaction file for Chase CC (Anantha) given the file name if not loaded as per dataload json file 
	 * and then update the data load status in the json
	 * @param csvPath
	 */
	public void processTransactionFile(Path csvPath) {
		LOGGER.info(" reading " + csvPath);
		try {
			List<ChaseCCCsvRecord> txns = ChaseCCCsvReader.read(csvPath);
			// identify the billing cycle these trans belong to
			Map<DateInterval, Integer> cycles = extractCycleIntervals(txns);
			
			//if these cycles are already loaded, then dont load any of these
			//check if these cycles are already loaded in dataload.json
			for (Map.Entry<DateInterval, Integer> entry : cycles.entrySet()) {
			    DateInterval interval = entry.getKey();
			    try {
			    	_store.findIntervalOrThrow( accountName, interval.getStartDate(), interval.getEndDate());
					// that means this is already loaded
					// nothing to do
			    }catch(java.lang.IllegalStateException e1) {
					// now convert these transactions to LedgerRecord
					for(int i = 0; i < txns.size(); i++) {
						ChaseCCCsvRecord chaseRec =  txns.get(i);
						_records.add(chaseRec.convertToLedgerRecord(accountName, (short)(i+1)));
					}
					
					// update the json for the internal
					LOGGER.info("updating dataload.json with a new interva " + interval.getStartDate() + "  and " + interval.getEndDate() );
					_store.upsertInterval(accountName,
							interval.getStartDate(),
							interval.getEndDate(),
                            "Loaded",
                            txns.size());
										
					// move the file to a processed folder
					Path targetDir =  Path.of(PROCESSED_FOLDER);
					Files.createDirectories(targetDir); // make sure it exists
					Path target = targetDir.resolve(csvPath.getFileName());
					LOGGER.info("moving " + csvPath + " to " + target);
					Files.move(csvPath, target, StandardCopyOption.REPLACE_EXISTING);
			    }
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
		ChaseCCLoad process = new ChaseCCLoad();
		//String ledgerFile =  "D:\\fin_docs\\Consolidated Ledger v2_mar2025.xlsx";
		String ledgerFile =  "D:\\fin_docs\\updated_ledger_01172026.xlsx";
		String sheetName =  "Runbook";
		String outputFile =  "D:\\fin_docs\\updated_ledger_01172026_latest.xlsx";
		
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
		
		// now read the Chase CC transaction files and add them to the ledger
		int beforeCount = process.getLedgerRecordCount();
		
		// now process all the files under the source folder
		Path folderPath = Paths.get(ChaseCCLoad.SOURCE_FOLDER);
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
