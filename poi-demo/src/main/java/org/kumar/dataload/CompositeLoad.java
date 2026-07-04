package org.kumar.dataload;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.AccountLoadSpecs.AccountSpec;
import org.kumar.dataload.util.DataloadBaseClass;
import org.kumar.dataload.validation.LedgerValidationContext;
import org.kumar.excel.LedgerExcelWriter;

/**
 * Runs all configured account loaders in a single pass:
 * <ol>
 *   <li>Read the ledger file once</li>
 *   <li>Load dataload.json once</li>
 *   <li>Run each account loader against the shared in-memory ledger</li>
 *   <li>Save the ledger and dataload.json once if any records were added</li>
 * </ol>
 */
public class CompositeLoad {
    private static final Logger LOGGER = LogManager.getLogger("CompositeLoad");

    public static void main(String[] args) throws Exception {
        String ledgerFile = "D:\\fin_docs\\updated_ledger_01192026_almost_final.xlsx";
        String sheetName = "Runbook";
        String outputFile = "D:\\fin_docs\\updated_ledger_01192026_composite.xlsx";

        final String baselineInputDir = "D:\\fin_docs\\";
        final String baselineProcessedDir = "\\Processed";

        LedgerValidationContext ctx = LedgerValidationContext.loadOnce(
                ledgerFile, sheetName, DataloadBaseClass.DATALOADJSON);
        int beforeCount = ctx.getLedger().size();
        LOGGER.info("Loaded ledger with {} records", beforeCount);

        String accountStatusConfig = System.getProperty(
                AccountLoadSpecs.ACCOUNT_STATUS_CONFIG_PROPERTY,
                AccountLoadSpecs.ACCOUNT_STATUS_CONFIG_JSON);
        LOGGER.info("Loading account status config from {}", accountStatusConfig);

        List<AccountSpec<?>> specs = AccountLoadSpecs.selectConfiguredSpecs(
                AccountLoadSpecs.buildAll(baselineInputDir, baselineProcessedDir),
                accountStatusConfig);

        for (AccountSpec<?> spec : specs) {
            runAccountLoader(spec, ctx);
        }

        int afterCount = ctx.getLedger().size();
        LOGGER.info("Ledger record count: {} -> {}", beforeCount, afterCount);

        if (afterCount != beforeCount) {
            ctx.getStore().save();
            LedgerExcelWriter.writeLedger(ctx.getLedger(), Path.of(outputFile));
            LOGGER.info("Saved updated ledger to {}", outputFile);
        } else {
            LOGGER.info("No new records added; ledger file not written");
        }
    }

    private static <T> void runAccountLoader(AccountSpec<T> spec, LedgerValidationContext ctx) throws Exception {
        LOGGER.info("Processing account: {}", spec.account);

        MonthlyLoad<T> loader = AccountLoadSpecs.createLoader(spec);
        AccountLoadSpecs.applyCycleStrategy(loader);
        loader.setLedger(ctx.getLedger());
        loader.setStore(ctx.getStore());
        loader.setAutoSaveJson(false);

        AccountLoadSpecs.scanAndProcess(loader);
    }
}
