package org.kumar.dataload.validation;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.AccountLoadSpecs;
import org.kumar.dataload.AccountLoadSpecs.AccountSpec;
import org.kumar.dataload.MonthlyLoad;
import org.kumar.excel.util.DateInterval;

public class LedgerCompleteness {
	private static final Logger LOGGER = LogManager.getLogger("LedgerCompleteness");
    public static final String DATALOADJSON = "dataload.json";

	public void runAll(LedgerValidationContext ctx, List<AccountSpec<?>> specs) throws Exception {
		for (AccountSpec<?> spec : specs) {
			checkCompleteness(spec, ctx);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> void checkCompleteness(
			AccountSpec<T> spec,
			LedgerValidationContext ctx) throws Exception {
		MonthlyLoad<T> process = AccountLoadSpecs.createLoader(spec);
		AccountLoadSpecs.applyCycleStrategy(process);

		LOGGER.info("setting the context for " + spec.account);
		process.setLedger(ctx.getLedger());
		process.setStore(ctx.getStore());
		process.setAutoSaveJson(false);

		LOGGER.info("building the cycle data");
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("sync'ing dataload.json");
		process.syncDataLoadJson(loadedData);
		LOGGER.info("finding missing..");
		List<DateInterval> missing = process.findMissingCycles(loadedData);
		if (missing == null) {
			LOGGER.info("None");
		} else {
			LOGGER.info(missing);
		}
  }

	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
  public static void main(String[] args) throws Exception {
    String ledgerFile = "D:\\fin_docs\\Updated_ledger_06212026.xlsx";
    String sheetName  = "Runbook";
    
    final String baselineInputDir = "D:\\fin_docs\\"; 
    final String baselineProcessedDir = "\\Processed";

    LedgerValidationContext ctx = LedgerValidationContext.loadOnce(ledgerFile, sheetName, DATALOADJSON);

    LedgerCompleteness lc = new LedgerCompleteness();

    String accountStatusConfig =
    		System.getProperty(AccountLoadSpecs.ACCOUNT_STATUS_CONFIG_PROPERTY, AccountLoadSpecs.ACCOUNT_STATUS_CONFIG_JSON);
    LOGGER.info("Loading account status config from " + accountStatusConfig);
    List<AccountSpec<?>> configuredSpecs = AccountLoadSpecs.selectConfiguredSpecs(
    		AccountLoadSpecs.buildAll(baselineInputDir, baselineProcessedDir),
    		accountStatusConfig);

    lc.runAll(ctx, configuredSpecs);
  }
}
