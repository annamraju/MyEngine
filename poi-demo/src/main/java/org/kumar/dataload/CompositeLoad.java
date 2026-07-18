package org.kumar.dataload;

import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.AccountLoadSpecs.AccountSpec;
import org.kumar.dataload.validation.LedgerCompleteness;
import org.kumar.dataload.validation.LedgerValidationContext;
import org.kumar.excel.LedgerExcelWriter;
import org.kumar.rules.RunCategorizer;

/**
 * End-to-end dataload pipeline:
 * <ol>
 *   <li>Load config and JSON settings</li>
 *   <li>Read the ledger file into memory once</li>
 *   <li>Run {@link LedgerCompleteness} (pre-load)</li>
 *   <li>Load all available transaction files for enabled accounts</li>
 *   <li>Run categorization on the in-memory ledger</li>
 *   <li>Run {@link LedgerCompleteness} (post-load)</li>
 *   <li>Save dataload.json and write the updated ledger file</li>
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
        int initialCount = ctx.getLedger().size();
        LOGGER.info("Loaded ledger with {} records", initialCount);

        LOGGER.info("Loading account status config from {}", config.accountStatusConfig);
        List<AccountSpec<?>> specs = AccountLoadSpecs.selectConfiguredSpecs(
                AccountLoadSpecs.buildAll(config.baselineInputDir, config.baselineProcessedDir),
                config.accountStatusConfig);

        LedgerCompleteness ledgerCompleteness = new LedgerCompleteness();

        LOGGER.info("Step 1: running LedgerCompleteness before dataload");
        ledgerCompleteness.runAll(ctx, specs);

        LOGGER.info("Step 2: loading transaction files for all enabled accounts");
        for (AccountSpec<?> spec : specs) {
            runAccountLoader(spec, ctx);
        }
        int afterLoadCount = ctx.getLedger().size();
        LOGGER.info("Ledger record count after dataload: {} -> {}", initialCount, afterLoadCount);

        LOGGER.info("Step 3: running categorization");
        RunCategorizer.categorizeInMemory(
                ctx.getLedger(),
                config.merchantMapJson,
                config.rulesJson,
                config.checkRulesJson);

        LOGGER.info("Step 4: running LedgerCompleteness after dataload and categorization");
        ledgerCompleteness.runAll(ctx, specs);

        LOGGER.info("Step 5: saving dataload.json and writing ledger to {}", config.outputFile);
        ctx.getStore().save();
        LedgerExcelWriter.writeLedger(ctx.getLedger(), Path.of(config.outputFile));
        LOGGER.info("Saved updated ledger with {} records", ctx.getLedger().size());
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
