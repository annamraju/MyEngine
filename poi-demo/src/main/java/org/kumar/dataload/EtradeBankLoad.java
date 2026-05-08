package org.kumar.dataload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.kumar.dataload.util.EtradeBankRecord;

public class EtradeBankLoad {
	/**
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		String srcfolder = "D:\\fin_docs\\etrade Bank";
		String ext = ".csv";
		String processedFolder = "D:\\fin_docs\\etrade Bank\\Processed";
		String ledgerFile = "D:\\fin_docs\\updated_ledger_01192026.xlsx";
		String newLedgerFile = "D:\\fin_docs\\updated_ledger_01192026_latest.xlsx";
		String sheetName = "Runbook";
		String account = "etrade Bank";
		MonthlyLoad<EtradeBankRecord> process = new MonthlyLoad<>(account, srcfolder, ext, processedFolder,
				EtradeBankRecord::readTransactionsFromCsv, // reader
				EtradeBankRecord::getTransactionDate, // date getter
				(rec, order) -> rec.convertToLedgerRecord(account, order) // convert
		);

		// read ledger
		process.readLedgerFile(ledgerFile, sheetName);

		// load dataload json
		process.loadDataLoadJson();

		int beforeCount = process.getLedgerRecordCount();

		// process folder
		Path folderPath = Paths.get(process.getSourceFolder());
		try (Stream<Path> paths = Files.list(folderPath)) {
			paths.filter(Files::isRegularFile)
					.filter(p -> p.toString().toLowerCase().endsWith(process.getFileExtension()))
					.forEach(process::processTransactionFile);
		}

		int afterCount = process.getLedgerRecordCount();

		if (afterCount != beforeCount) {
			process.saveExcelAndJson(Path.of(newLedgerFile));
		}
	}

}
