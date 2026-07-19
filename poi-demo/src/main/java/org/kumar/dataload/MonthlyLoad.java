package org.kumar.dataload;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.util.DataLoadJsonStore;
import org.kumar.dataload.util.DataloadBaseClass;
import org.kumar.excel.util.DateInterval;
import org.kumar.excel.util.LedgerRecord;

public class MonthlyLoad<T> extends DataloadBaseClass {
	private static final Logger LOGGER = LogManager.getLogger("MonthlyLoad");
	
    /**
     * Strategy for turning a transaction date into a "cycle" (DateInterval),
     * and for computing missing cycles given a set of present intervals.
     *
     * Examples:
     *  - Calendar month cycles (1st -> last day of month)
     *  - Credit card billing cycles (e.g., 15th -> 14th)
     *  - Your 26th -> 25th cycles
     */
    public interface CycleStrategy {
        DateInterval toInterval(LocalDate txnDate);
        DateInterval toInterval(Date txnDate);
        List<DateInterval> findMissing(Set<DateInterval> presentIntervals);
    }

    /** Default: calendar-month cycles (1st -> last day of month). */
    public static final class CalendarMonthCycleStrategy implements CycleStrategy {
        @Override
        public DateInterval toInterval(LocalDate txnDate) {
            return DateInterval.getMonthlyDateInterval(txnDate);
        }
        
        @Override
        public DateInterval toInterval(Date txnDate) {
            if (txnDate == null) return null;

            return DateInterval.getMonthlyDateInterval(txnDate.toInstant()
                       										.atZone(ZoneId.systemDefault())
                       										.toLocalDate());
        }

        @Override
        public List<DateInterval> findMissing(Set<DateInterval> presentIntervals) {
            return DateInterval.findMonthlyMissingCycles(presentIntervals);
        }
    }

    /**
     * Fixed start-day/end-day cycle (e.g., 15 -> 14 or 26 -> 25).
     * Requires DateInterval.getDateInterval(LocalDate, short, short)
     * and DateInterval.findMissingCycles(Set<DateInterval>, short, short)
     * (as used in your ChaseCCLoad.java).
     */
    public static final class FixedDayCycleStrategy implements CycleStrategy {
        private final short cycleStartDay;
        private final short cycleEndDay;

        public FixedDayCycleStrategy(short cycleStartDay, short cycleEndDay) {
            if (cycleStartDay < 1 || cycleStartDay > 31) {
                throw new IllegalArgumentException("cycleStartDay must be 1..31, got " + cycleStartDay);
            }
            if (cycleEndDay < 1 || cycleEndDay > 31) {
                throw new IllegalArgumentException("cycleEndDay must be 1..31, got " + cycleEndDay);
            }
            this.cycleStartDay = cycleStartDay;
            this.cycleEndDay = cycleEndDay;
        }

        public short getCycleStartDay() { return cycleStartDay; }
        public short getCycleEndDay() { return cycleEndDay; }

        @Override
        public DateInterval toInterval(LocalDate txnDate) {
            return DateInterval.getDateInterval(txnDate, cycleStartDay, cycleEndDay);
        }

        @Override
        public DateInterval toInterval(Date txnDate) {
        	if (txnDate == null) return null;

        	    return DateInterval.getDateInterval(txnDate.toInstant()
        	               								.atZone(ZoneId.systemDefault())
        	               								.toLocalDate(),cycleStartDay, cycleEndDay);
        }
        
        @Override
        public List<DateInterval> findMissing(Set<DateInterval> presentIntervals) {
            return DateInterval.findMissingCycles(presentIntervals, cycleStartDay, cycleEndDay);
        }
    }
	

    private boolean autoSaveJson = true;

    public void setAutoSaveJson(boolean v) { this.autoSaveJson = v; }

    /** Cycle strategy used by this loader; default is calendar month. */
    //TODO: why new() here 
    private CycleStrategy cycleStrategy = new CalendarMonthCycleStrategy();

    /** Override the cycle definition for this account (e.g., new FixedDayCycleStrategy((short)15,(short)14)). */
    public void setCycleStrategy(CycleStrategy strategy) {
        this.cycleStrategy = (strategy == null) ? new CalendarMonthCycleStrategy() : strategy;
    }
    public CycleStrategy getCycleStrategy() { return cycleStrategy; }
    
    @Override
    public void syncDataLoadJson(Map<DateInterval, Integer> loadedData) {
    	// ✅ only load if not already injected/cached
        loadDataLoadJson();

        try {
        	_store.findAccountOrThrow(accountName);
        } catch (IllegalStateException e) {
        	_store.upsertAccount(accountName);
        }

        for (Map.Entry<DateInterval, Integer> entry : loadedData.entrySet()) {
        	DateInterval di = entry.getKey();
            Integer recordCount = entry.getValue();

            try {
            	_store.findIntervalOrThrow(accountName, di.getStartDate(), di.getEndDate());
            } catch (IllegalStateException e2) {
            	_store.upsertInterval(accountName, di.getStartDate(), di.getEndDate(), "Loaded", recordCount);
            }
        }

        // ✅ don’t save 20 times during validation
        if (autoSaveJson) saveDataLoadJson();
    }
        
    @FunctionalInterface
    public interface RecordReader<T> {
        List<T> read(Path path) throws IOException;
    }

    protected final String accountName;
    public final String SOURCE_FOLDER;
    public final String transactionExt;
    public final String PROCESSED_FOLDER;

    // “Injected” behaviors
    private final RecordReader<T> reader;
    private final Function<T, LocalDate> txnDateGetter;
    private final BiFunction<T, Short, LedgerRecord> toLedgerRecord;

    public MonthlyLoad(
            String account,
            String SRC_FLDR,
            String ext,
            String PRC_FLDR,
            RecordReader<T> reader,
            Function<T, LocalDate> txnDateGetter,
            BiFunction<T, Short, LedgerRecord> toLedgerRecord
    ) {
        super();
        this.accountName = account;
        this.SOURCE_FOLDER = SRC_FLDR;
        this.transactionExt = ext;
        this.PROCESSED_FOLDER = PRC_FLDR;

        this.reader = reader;
        this.txnDateGetter = txnDateGetter;
        this.toLedgerRecord = toLedgerRecord;
    }

    @Override
    public String getAccountName() {
        return accountName;
    }

    public String getProcessedFolder() { return PROCESSED_FOLDER; }
    public String getFileExtension() { return transactionExt; }
    public String getSourceFolder() { return SOURCE_FOLDER; }

    @Override
    public Map<DateInterval, Integer> buildCycleData() {
        Iterator<LedgerRecord> iter = _records.iterator();
        HashMap<DateInterval, Integer> loadedData = new HashMap<>();

        while (iter.hasNext()) {
            LedgerRecord rec = iter.next();
            if (rec.getAccount().equalsIgnoreCase(accountName)) {
                DateInterval cycle = cycleStrategy.toInterval(rec.getTransDate());
                loadedData.put(cycle, loadedData.getOrDefault(cycle, 0) + 1);
            }
        }
        return loadedData;
    }
    
    public List<DateInterval> findMissingCycles(Map<DateInterval, Integer> loadedData) {
    	if(loadedData == null || loadedData.isEmpty()) return null;
        List<DateInterval> missingcycles = cycleStrategy.findMissing(loadedData.keySet());
        if(missingcycles == null) return null;
        List<DateInterval> finalMissing = new ArrayList<>();

        for (DateInterval di : missingcycles) {
            try {
                DataLoadJsonStore.LoadInterval li =
                        _store.findIntervalOrThrow(accountName, di.getStartDate(), di.getEndDate());
                if (li.numberOfRecords == 0 && li.status != null && li.status.trim().equalsIgnoreCase("Loaded")) {
                    // marked completed -> ignore
                } else {
                    LOGGER.error("**** cycle missing, but numberOfRecords != 0 or status != Loaded");
                    LOGGER.error("interval " + li.startDate + " and " + li.endDate + " status " + li.status + " number of records " + li.numberOfRecords);
                }
            } catch (IllegalStateException notFound) {
                finalMissing.add(di);
            }
        }
        return finalMissing;
    }

    /** Build counts of cycles present in a list of transactions. */
    public Map<DateInterval, Integer> extractCycleCounts(List<T> txns) {
        Map<DateInterval, Integer> cycles = new HashMap<>();
        for (T txn : txns) {
            LocalDate d = txnDateGetter.apply(txn);
            DateInterval cycle = cycleStrategy.toInterval(d);
            cycles.put(cycle, cycles.getOrDefault(cycle, 0) + 1);
        }
        return cycles;
    }

    /*
    // Generic cycle extraction
    public Map<DateInterval, Integer> extractCycleIntervals(List<T> txns) {
        Map<DateInterval, Integer> cycles = new HashMap<>();
        for (T txn : txns) {
            LocalDate d = txnDateGetter.apply(txn);
            DateInterval cycle = cycleStrategy.toInterval(d);
            cycles.putIfAbsent(cycle, 1);
        }
        return cycles;
    }
	*/
    
    /** Group transactions by cycle interval (required when a file can span multiple cycles). */
    private Map<DateInterval, List<T>> groupTransactionsByInterval(List<T> txns) {
        Map<DateInterval, List<T>> byInterval = new HashMap<>();
        for (T txn : txns) {
            LocalDate d = txnDateGetter.apply(txn);
            DateInterval interval = cycleStrategy.toInterval(d);
            byInterval.computeIfAbsent(interval, k -> new ArrayList<>()).add(txn);
        }
        return byInterval;
    }

    // Generic transaction processing
    public void processTransactionFile(Path csvPath) {
        LOGGER.info("reading " + csvPath);
        try {
            List<T> txns = reader.read(csvPath);

            //Map<DateInterval, Integer> cycles = extractCycleIntervals(txns);
            // IMPORTANT: one file may contain multiple cycles -> group by interval
            Map<DateInterval, List<T>> txnsByInterval = groupTransactionsByInterval(txns);

            for (Map.Entry<DateInterval, List<T>> e : txnsByInterval.entrySet()) {
                DateInterval interval = e.getKey();
                List<T> intervalTxns = e.getValue();
                try {
                    _store.findIntervalOrThrow(accountName, interval.getStartDate(), interval.getEndDate());
                    // already loaded -> do nothing
                } catch (IllegalStateException notLoadedYet) {

                    // convert these transactions to LedgerRecord
                    for (int i = 0; i < intervalTxns.size(); i++) {
                        T txn = intervalTxns.get(i);
                        _records.add(toLedgerRecord.apply(txn, (short) (i + 1)));
                    }

                    LOGGER.info("updating dataload.json with interval "
                            + interval.getStartDate() + " to " + interval.getEndDate());

                    _store.upsertInterval(
                            accountName,
                            interval.getStartDate(),
                            interval.getEndDate(),
                            "Loaded",
                            txns.size()
                    );

                    if (autoSaveJson) saveDataLoadJson();
                    
                }
            }
            
            // move file
            Path targetDir = Path.of(PROCESSED_FOLDER);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(csvPath.getFileName());
            LOGGER.info("moving " + csvPath + " to " + target);
            Files.move(csvPath, target, StandardCopyOption.REPLACE_EXISTING);

        } catch (Exception e) {
            LOGGER.error("Failed processing " + csvPath, e);
        }
    }    
}
