package org.kumar.dataload.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.excel.LedgerExcelWriter;
import org.kumar.excel.ReadLedgerFile;
import org.kumar.excel.util.DateInterval;
import org.kumar.excel.util.LedgerRecord;

public abstract class DataloadBaseClass {
    private static final Logger LOGGER = LogManager.getLogger("DataloadBaseClass");

    protected List<LedgerRecord> _records = null;
    protected DataLoadJsonStore _store = null;

    public static SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
    public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final String DATALOADJSON = "dataload.json";

    // ✅ Shared caches
    private static final ConcurrentMap<String, List<LedgerRecord>> LEDGER_CACHE = new ConcurrentHashMap<>();
    private static final AtomicReference<DataLoadJsonStore> STORE_CACHE = new AtomicReference<>();

    abstract public Map<DateInterval, Integer> buildCycleData();
    abstract public List<DateInterval> findMissingCycles(Map<DateInterval, Integer> loadedData);
    abstract public void processTransactionFile(Path csvPath);
    abstract public void syncDataLoadJson(Map<DateInterval, Integer> loadedData);
    abstract public String getAccountName();

    // ✅ Optional injection (best for validation runner)
    public void setLedger(List<LedgerRecord> ledger) {
        this._records = Objects.requireNonNull(ledger, "ledger");
    }

    public void setStore(DataLoadJsonStore store) {
        this._store = Objects.requireNonNull(store, "store");
    }

    public List<LedgerRecord> getLedger() { return _records; }
    public DataLoadJsonStore getStore() { return _store; }

    public void readLedgerFile(String ledgerFileName, String sheetName) throws FileNotFoundException {
        if (_records != null) return; // already injected/loaded

        String key = ledgerFileName + "::" + sheetName;
        _records = LEDGER_CACHE.computeIfAbsent(key, k -> {
            try {
                ReadLedgerFile process = new ReadLedgerFile();
                List<LedgerRecord> r = process.readFile(ledgerFileName, sheetName);
                LOGGER.info("done reading the ledger (cached) for key=" + k);
                return r;
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void loadDataLoadJson() {
        if (_store != null) return; // already injected/loaded

        DataLoadJsonStore cached = STORE_CACHE.get();
        if (cached != null) {
            _store = cached;
            return;
        }

        try {
            DataLoadJsonStore created = DataLoadJsonStore.loadFromResourceToDefaultConfig(DATALOADJSON);
            STORE_CACHE.compareAndSet(null, created);
            _store = STORE_CACHE.get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveDataLoadJson() {
        if (_store == null) return;
        try {
            _store.save();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveExcelAndJson(Path p) throws Exception {
        saveDataLoadJson();
        LedgerExcelWriter.writeLedger(_records, p);
    }

    public int getLedgerRecordCount() {
        return (_records == null) ? 0 : _records.size();
    }
}
