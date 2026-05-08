/**
 * 
 */
package org.kumar.excel;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.excel.util.LedgerRecord;

/**
 * 
 */
public class CheckForDups {
	
	private static final Logger LOGGER = LogManager.getLogger("CheckForDups");
	public static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
	
	List<LedgerRecord> _records = null;
	Map<String, Integer> _dupMap= null;

	/**
	 * 
	 * @param ledgerFileName
	 * @param sheetName
	 */
	public void readLedgerFile(String ledgerFileName, String sheetName) {
		ReadLedgerFile process =  new ReadLedgerFile();
		try {
			_records =  process.readFile(ledgerFileName, sheetName);			
		}catch(FileNotFoundException e) {
			e.printStackTrace();
		}
		LOGGER.info("done reading the ledger");
	}
	
	public void loadIntoMap() {
		if(_records.isEmpty())
			return;
		
		_dupMap = new HashMap<String, Integer>();
		Iterator<LedgerRecord> iter = _records.iterator();
		while(iter.hasNext()) {
			LedgerRecord rec = iter.next();
			//construct a string to be used as a key
			StringBuffer temp = new StringBuffer();
			temp.append(rec.getAccount());
			temp.append(sdf.format(rec.getTransDate()));
			temp.append(rec.getDescription());
			temp.append(rec.getAmount());
			String key = temp.toString();
			if(_dupMap.containsKey(key)) {
				LOGGER.error("there is already a record with this key " + key) ;
			}else {
				_dupMap.put(key, 1);
			}
		}
		
	}
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		CheckForDups process =  new CheckForDups();
		String ledgerFile =  "D:\\fin_docs\\Consolidated Ledger v2_mar2025.xlsx";
		String sheetName =  "Runbook";
		process.readLedgerFile(ledgerFile, sheetName);	
		
		process.loadIntoMap();
	}

}
