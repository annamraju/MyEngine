package org.kumar.dataload.validation;

import java.io.IOException;
import java.util.List;

import org.kumar.excel.ReadLedgerFile;
import org.kumar.excel.util.LedgerRecord;
import org.kumar.dataload.util.DataLoadJsonStore;

public final class LedgerValidationContext {
  private final List<LedgerRecord> ledger;
  private final DataLoadJsonStore store;

  private LedgerValidationContext(List<LedgerRecord> ledger, DataLoadJsonStore store) {
    this.ledger = ledger;
    this.store = store;
  }

  public List<LedgerRecord> getLedger() { return ledger; }
  public DataLoadJsonStore getStore() { return store; }

  public static LedgerValidationContext loadOnce(String ledgerFile, String sheetName, String dataloadJson) throws IOException {
    // Use your existing excel reader that MonthlyLoad uses internally
    // e.g. ReadLedgerFile.readFile(...)

	  ReadLedgerFile fileReader =  new ReadLedgerFile();
	  List<LedgerRecord> ledger = fileReader.readFile(ledgerFile, sheetName); // adjust to your API
	  DataLoadJsonStore store = DataLoadJsonStore.loadFromResourceToDefaultConfig(dataloadJson);        // adjust to your API
    return new LedgerValidationContext(ledger, store);
  }
}
