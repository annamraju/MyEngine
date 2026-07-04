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
 *
 * <p>Paths and folders are read from {@code composite-load.json} on the classpath,
 * or from a file path set via the {@code composite.load.config} system property.</p>
 */
public class CompositeLoad {
    private static final Logger LOGGER = LogManager.getLogger("CompositeLoad");

    public static void main(String[] args) throws Exception {
        CompositeLoadConfig config = CompositeLoadConfig.loadDefault();
        LOGGER.info("Loaded composite load config: ledgerFile={}, outputFile={}, baselineInputDir={}",
                config.ledgerFile, config.outputFile, config.baselineInputDir);

        LedgerValidationContext ctx = LedgerValidationContext.loadOnce(
                config.ledgerFile, config.sheetName, config.dataloadJson);
        int beforeCount = ctx.getLedger().size();
        LOGGER.info("Loaded ledger with {} records", beforeCount);

        LOGGER.info("Loading account status config from {}", config.accountStatusConfig);
        List<AccountSpec<?>> specs = AccountLoadSpecs.selectConfiguredSpecs(
                AccountLoadSpecs.buildAll(config.baselineInputDir, config.baselineProcessedDir),
                config.accountStatusConfig);

        for (AccountSpec<?> spec : specs) {
            runAccountLoader(spec, ctx);
        }

        int afterCount = ctx.getLedger().size();
        LOGGER.info("Ledger record count: {} -> {}", beforeCount, afterCount);

        if (afterCount != beforeCount) {
            ctx.getStore().save();
            LedgerExcelWriter.writeLedger(ctx.getLedger(), Path.of(config.outputFile));
            LOGGER.info("Saved updated ledger to {}", config.outputFile);
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
