package org.kumar.dataload.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.dataload.MonthlyLoad;
import org.kumar.dataload.util.BOACheckingRecord;
import org.kumar.dataload.util.BofaCCRecord;
import org.kumar.dataload.util.ChaseCCCsvRecord;
import org.kumar.dataload.util.ChaseCCananthaCsvRecord;
import org.kumar.dataload.util.ChaseCheckingRecord;
import org.kumar.dataload.util.CitiSavingsRecord;
import org.kumar.dataload.util.EtradeBankRecord;
import org.kumar.dataload.util.GTEFCUCheckingRecord;
import org.kumar.dataload.util.HSARecord;
import org.kumar.dataload.util.MarcusSavingsRecord;
import org.kumar.excel.util.DateInterval;
import org.kumar.excel.util.LedgerRecord;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

public class LedgerCompleteness {
	private static final Logger LOGGER = LogManager.getLogger("LedgerCompleteness");
    public static final String DATALOADJSON = "dataload.json";
    public static final String ACCOUNT_STATUS_CONFIG_JSON = "ledger-completeness-accounts.json";
    public static final String ACCOUNT_STATUS_CONFIG_PROPERTY = "ledger.completeness.accounts";

	public static final class AccountSpec<T> {
		public final String account;
		public final String srcFolder;
		public final String ext;
		public final String processedFolder;

		public final MonthlyLoad.RecordReader<T> reader;           // e.g., MarcusSavingsRecord::readTransactionsFromPdf
		public final Function<T, java.time.LocalDate> dateGetter;
		public final BiFunction<T, Short, LedgerRecord> toLedger;

		public AccountSpec(
				String account,
				String srcFolder,
				String ext,
				String processedFolder,
				MonthlyLoad.RecordReader<T> reader,
				Function<T, java.time.LocalDate> dateGetter,
				BiFunction<T, Short, LedgerRecord> toLedger) {
			this.account = account;
			this.srcFolder = srcFolder;
			this.ext = ext;
			this.processedFolder = processedFolder;
			this.reader = reader;
			this.dateGetter = dateGetter;
			this.toLedger = toLedger;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static final class AccountStatusConfig {
		public List<AccountStatus> accounts = new ArrayList<>();

		public static AccountStatusConfig load(String configLocation) throws IOException {
			ObjectMapper mapper = new ObjectMapper();

			try (InputStream is = openConfig(configLocation)) {
				return mapper.readValue(is, AccountStatusConfig.class);
			}
		}

		private static InputStream openConfig(String configLocation) throws IOException {
			Path configPath = Path.of(configLocation);
			if (Files.exists(configPath)) {
				return Files.newInputStream(configPath);
			}

			ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
			InputStream resourceStream = classLoader.getResourceAsStream(configLocation);
			if (resourceStream != null) {
				return resourceStream;
			}

			throw new IOException("Account status config not found: " + configLocation);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public static final class AccountStatus {
		public String account;
		public String status;
		public Boolean enabled;

		public boolean isEnabled() {
			if (enabled != null) {
				return enabled.booleanValue();
			}

			if (status == null || status.isBlank()) {
				return true;
			}

			switch (status.trim().toUpperCase(Locale.ROOT)) {
				case "ACTIVE":
				case "ENABLED":
				case "INCLUDE":
				case "INCLUDED":
					return true;
				case "INACTIVE":
				case "DISABLED":
				case "EXCLUDE":
				case "EXCLUDED":
					return false;
				default:
					throw new IllegalArgumentException("Unsupported account status '" + status + "' for account " + account);
			}
		}
	}

	private <T> void checkCompleteness(
			String ledgerFile,
			String sheetName,
			AccountSpec<T> spec,
			LedgerValidationContext ctx) throws Exception {
		MonthlyLoad<T> process =
				new MonthlyLoad<>(
						spec.account,
						spec.srcFolder,
						spec.ext,
						spec.processedFolder,
						spec.reader,
						spec.dateGetter,
						spec.toLedger
        );
		
    	switch(process.getAccountName()) {
    		case "BOA CC":
				process.setCycleStrategy(new MonthlyLoad.FixedDayCycleStrategy((short)26, (short)25));
				break;
    		case "Chase CC (Anantha)":
				process.setCycleStrategy(new MonthlyLoad.FixedDayCycleStrategy((short)18, (short)17));
				break;
    		case "Chase CC":
				process.setCycleStrategy(new MonthlyLoad.FixedDayCycleStrategy((short)15, (short)14));
				break;
			default:
				//nothing to do
				break;
    	}
	
		LOGGER.info("setting the context for " + spec.account);
		// ✅ BIG change: inject shared context (no re-reading ledger/json per account)
		process.setLedger(ctx.getLedger());       // you’ll add these setters (see section 2)
		process.setStore(ctx.getStore());

		LOGGER.info("building the cycle data");
		Map<DateInterval, Integer> loadedData = process.buildCycleData();
		LOGGER.info("sync\'ing dataload.json");
		process.syncDataLoadJson(loadedData);
		LOGGER.info("finding missing..");
		List<DateInterval> missing = process.findMissingCycles(loadedData);
		if(missing == null) LOGGER.info("None");
		else LOGGER.info(missing);
  }

	private List<AccountSpec<?>> selectConfiguredSpecs(
			List<AccountSpec<?>> availableSpecs,
			String configLocation) throws IOException {
		AccountStatusConfig config = AccountStatusConfig.load(configLocation);
		if (config.accounts == null || config.accounts.isEmpty()) {
			throw new IllegalArgumentException("No accounts configured in " + configLocation);
		}

		Map<String, AccountSpec<?>> availableByAccount = new LinkedHashMap<>();
		for (AccountSpec<?> spec : availableSpecs) {
			availableByAccount.put(spec.account, spec);
		}

		Set<String> configuredAccounts = new HashSet<>();
		List<AccountSpec<?>> selectedSpecs = new ArrayList<>();
		for (AccountStatus accountStatus : config.accounts) {
			if (accountStatus.account == null || accountStatus.account.isBlank()) {
				throw new IllegalArgumentException("Account status config contains a blank account name");
			}

			String account = accountStatus.account.trim();
			if (!configuredAccounts.add(account)) {
				LOGGER.warn("Ignoring duplicate account status config for " + account);
				continue;
			}

			AccountSpec<?> spec = availableByAccount.get(account);
			if (spec == null) {
				LOGGER.warn("Ignoring account status config for unsupported account: " + account);
				continue;
			}

			if (accountStatus.isEnabled()) {
				selectedSpecs.add(spec);
			} else {
				LOGGER.info("Skipping disabled account: " + account);
			}
		}

		if (selectedSpecs.isEmpty()) {
			throw new IllegalArgumentException("No enabled accounts found in " + configLocation);
		}

		return selectedSpecs;
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

    // ✅ Load ONCE
    LedgerValidationContext ctx = LedgerValidationContext.loadOnce(ledgerFile, sheetName, DATALOADJSON);

    LedgerCompleteness lc = new LedgerCompleteness();

    List<AccountSpec<?>> specs = List.of(
      new AccountSpec<>(
	        "Marcus Savings",
	        baselineInputDir + "Marcus Savings",
	        ".pdf",
	        baselineInputDir + "Marcus Savings" + baselineProcessedDir,
	        MarcusSavingsRecord::readTransactionsFromPdf,
	        MarcusSavingsRecord::getTransactionDate,
	        (rec, order) -> rec.convertToLedgerRecord("Marcus Savings", order)
      ),
      new AccountSpec<>(
	        "HSA",
	        baselineInputDir + "HSA",
	        ".csv",
	        baselineInputDir + "HSA" + baselineProcessedDir,
	        HSARecord::readTransactionsFromCsv,
	        HSARecord::getTransactionDate,
	        (rec, order) -> rec.convertToLedgerRecord("HSA", order)
      ),
      new AccountSpec<>(
    	        "GTEFCU CC",
    	        baselineInputDir + "GTEFCU CC",
    	        ".csv",
    	        baselineInputDir + "GTEFCU CC" + baselineProcessedDir,
    	        GTEFCUCheckingRecord::readTransactionsFromCsv,
    	        GTEFCUCheckingRecord::getTransactionDate,
    	        (rec, order) -> rec.convertToLedgerRecord("GTEFCU CC", order)
    	      ),
      new AccountSpec<>(
  	        "GTEFCU CD",
  	        baselineInputDir + "GTEFCU CD",
  	        ".csv",
  	        baselineInputDir + "GTEFCU CD" + baselineProcessedDir,
  	        GTEFCUCheckingRecord::readTransactionsFromCsv,
  	        GTEFCUCheckingRecord::getTransactionDate,
  	        (rec, order) -> rec.convertToLedgerRecord("GTEFCU CD", order)
  	      ),
      new AccountSpec<>(
    	        "GTEFCU Savings",
    	        baselineInputDir + "GTEFCU Savings",
    	        ".csv",
    	        baselineInputDir + "GTEFCU Savings" + baselineProcessedDir,
    	        GTEFCUCheckingRecord::readTransactionsFromCsv,
    	        GTEFCUCheckingRecord::getTransactionDate,
    	        (rec, order) -> rec.convertToLedgerRecord("GTEFCU Savings", order)
    	      ),
      new AccountSpec<>(
  	        "GTEFCU Checking",
  	        baselineInputDir + "GTEFCU Checking",
  	        ".csv",
  	        baselineInputDir + "GTEFCU Checking" + baselineProcessedDir,
  	        GTEFCUCheckingRecord::readTransactionsFromCsv,
  	        GTEFCUCheckingRecord::getTransactionDate,
  	        (rec, order) -> rec.convertToLedgerRecord("GTEFCU Checking", order)
  	      ),
      new AccountSpec<>(
    	        "etrade Bank",
    	        baselineInputDir + "etrade Bank",
    	        ".csv",
    	        baselineInputDir + "etrade Bank" + baselineProcessedDir,
    	        EtradeBankRecord::readTransactionsFromCsv,
    	        EtradeBankRecord::getTransactionDate,
    	        (rec, order) -> rec.convertToLedgerRecord("etrade Bank", order)
    	      ),
      new AccountSpec<>(
  	        "Citi Savings",
  	        baselineInputDir + "Citi Savings",
  	        ".csv",
  	        baselineInputDir + "Citi Savings" + baselineProcessedDir,
  	        CitiSavingsRecord::readTransactionsFromCsv,
  	        CitiSavingsRecord::getTransactionDate,
  	        (rec, order) -> rec.convertToLedgerRecord("Citi Savings", order)
  	      ),
      new AccountSpec<>(
    	        "Chase Checking (Anantha LLC)",
    	        baselineInputDir + "Chase Checking (Anantha LLC)",
    	        ".csv",
    	        baselineInputDir + "Chase Checking (Anantha LLC)" + baselineProcessedDir,
    	        ChaseCheckingRecord::readTransactionsFromCsv,
    	        ChaseCheckingRecord::getTransactionDate,
    	        (rec, order) -> rec.convertToLedgerRecord("Chase Checking (Anantha LLC)", order)
    	      ),
      new AccountSpec<>(
  	        "Chase Checking",
  	        baselineInputDir + "Chase Checking",
  	        ".csv",
  	        baselineInputDir + "Chase Checking" + baselineProcessedDir,
  	        ChaseCheckingRecord::readTransactionsFromCsv,
  	        ChaseCheckingRecord::getTransactionDate,
  	        (rec, order) -> rec.convertToLedgerRecord("Chase Checking", order)
  	      ),
      new AccountSpec<>(
    	        "BOA Savings",
    	        baselineInputDir + "BOA Savings",
    	        ".csv",
    	        baselineInputDir + "BOA Savings" + baselineProcessedDir,
    	        BOACheckingRecord::readTransactionsFromCsv,
    	        BOACheckingRecord::getTransactionDate,
    	        (rec, order) -> rec.convertToLedgerRecord("BOA Savings", order)
    	      ),
      new AccountSpec<>(
  	        "BOA Checking(Anjali)",
  	        baselineInputDir + "BOA Checking(Anjali)",
  	        ".csv",
  	        baselineInputDir + "BOA Checking(Anjali)" + baselineProcessedDir,
  	        BOACheckingRecord::readTransactionsFromCsv,
  	        BOACheckingRecord::getTransactionDate,
  	        (rec, order) -> rec.convertToLedgerRecord("BOA Checking(Anjali)", order)
  	      ),
      new AccountSpec<>(
    	        "BOA Checking(Ananya)",
    	        baselineInputDir + "BOA Checking(Ananya)",
    	        ".csv",
    	        baselineInputDir + "BOA Checking(Ananya)" + baselineProcessedDir,
    	        BOACheckingRecord::readTransactionsFromCsv,
    	        BOACheckingRecord::getTransactionDate,
    	        (rec, order) -> rec.convertToLedgerRecord("BOA Checking(Ananya)", order)
    	      ),
      new AccountSpec<>(
  	        "BOA Checking",
  	        baselineInputDir + "BOA Checking",
  	        ".csv",
  	        baselineInputDir + "BOA Checking" + baselineProcessedDir,
  	        BOACheckingRecord::readTransactionsFromCsv,
  	        BOACheckingRecord::getTransactionDate,
  	        (rec, order) -> rec.convertToLedgerRecord("BOA Checking", order)
  	      ),
      new AccountSpec<>(
    	        "BOA CC",
    	        baselineInputDir + "BOA CC",
    	        ".csv",
    	        baselineInputDir + "BOA CC" + baselineProcessedDir,
    	        BofaCCRecord::readTransactionsFromCsv,
    	        BofaCCRecord::getPostedDate,
    	        (rec, order) -> rec.convertToLedgerRecord("BOA CC", order)
    	      ),
      new AccountSpec<>(
  	        "Chase CC (Anantha)",
  	        baselineInputDir + "Chase CC (Anantha)",
  	        ".csv",
  	        baselineInputDir + "Chase CC (Anantha)" + baselineProcessedDir,
  	        ChaseCCananthaCsvRecord::readTransactionsFromCsv,
  	        ChaseCCananthaCsvRecord::getTransactionDate,
  	        (rec, order) -> rec.convertToLedgerRecord("Chase CC (Anantha)", order)
  	      ),  
      new AccountSpec<>(
    	        "Chase CC",
    	        baselineInputDir + "Chase CC",
    	        ".csv",
    	        baselineInputDir + "Chase CC" + baselineProcessedDir,
    	        ChaseCCCsvRecord::readTransactionsFromCsv,
    	        ChaseCCCsvRecord::getTransactionDate,
    	        (rec, order) -> rec.convertToLedgerRecord("Chase CC", order)
    	      )  
    );

    String accountStatusConfig =
    		System.getProperty(ACCOUNT_STATUS_CONFIG_PROPERTY, ACCOUNT_STATUS_CONFIG_JSON);
    LOGGER.info("Loading account status config from " + accountStatusConfig);
    List<AccountSpec<?>> configuredSpecs = lc.selectConfiguredSpecs(specs, accountStatusConfig);

    for (AccountSpec<?> s : configuredSpecs) {
      lc.checkCompleteness(ledgerFile, sheetName, (AccountSpec<Object>) s, ctx);
      Thread.sleep(1000);
    }
  }
}
