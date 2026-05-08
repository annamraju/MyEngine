/**
 * 
 */
package org.kumar.dataload.util;

import org.kumar.excel.util.LedgerRecord;

/**
 * 
 */
public interface LedgerInterface {
	
	public LedgerRecord convertToLedgerRecord(String accountName, short order);

}
