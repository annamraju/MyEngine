package org.kumar.dataload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.kumar.dataload.util.EtradeBrokerageRecord;

public class EtradeBrokerageLoad {

    public static void main(String[] args) throws Exception {
        String srcfolder = "D:\\fin_docs\\etrade Brokerage";
        String ext = ".pdf";
        String processedFolder = "D:\\fin_docs\\etrade Brokerage\\Processed";
        String ledgerFile = "D:\\fin_docs\\updated_ledger_01192026.xlsx";
        String newLedgerFile = "D:\\fin_docs\\updated_ledger_01192026_latest.xlsx";
        String sheetName = "Runbook";
        String account = "etrade Brokerage";

        MonthlyLoad<EtradeBrokerageRecord> process = new MonthlyLoad<>(account, srcfolder, ext, processedFolder,
                EtradeBrokerageRecord::readTransactionsFromPdf,
                EtradeBrokerageRecord::getTransactionDate,
                (rec, order) -> rec.convertToLedgerRecord(account, order)
        );

        process.readLedgerFile(ledgerFile, sheetName);
        process.loadDataLoadJson();

        int beforeCount = process.getLedgerRecordCount();

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
