package org.kumar.dataload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.kumar.dataload.util.EtradeBrokerageRecord;
import org.kumar.excel.util.LedgerRecord;
import org.kumar.rules.LedgerRecordCategorizer;
import org.kumar.rules.LedgerRecordCategorizer.CategorizationResult;
import org.kumar.rules.LedgerRecordCategorizer.Engine;

public class EtradeBrokerageLoad {

    public static void main(String[] args) throws Exception {
        String srcfolder = "D:\\fin_docs\\etrade Brokerage";
        String ext = ".pdf";
        String processedFolder = "D:\\fin_docs\\etrade Brokerage\\Processed";
        String ledgerFile = "D:\\fin_docs\\Updated_ledger_06212026.xlsx";
        String newLedgerFile = "D:\\fin_docs\\Updated_ledger_06212026_new.xlsx";
        String sheetName = "Runbook";
        String account = "etrade Brokerage";

        Engine categorizer = LedgerRecordCategorizer.load(
                "merchant_map.json",
                "rules.json",
                "check_rules.json"
        );

        MonthlyLoad<EtradeBrokerageRecord> process = new MonthlyLoad<>(account, srcfolder, ext, processedFolder,
                EtradeBrokerageRecord::readTransactionsFromPdf,
                EtradeBrokerageRecord::getTransactionDate,
                (rec, order) -> categorizeRecord(categorizer, rec, account, order)
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

    static LedgerRecord categorizeRecord(Engine categorizer, EtradeBrokerageRecord rec, String account, short order) {
        LedgerRecord ledgerRecord = rec.convertToLedgerRecord(account, order);
        CategorizationResult result = LedgerRecordCategorizer.categorizeOne(categorizer, ledgerRecord);
        LedgerRecordCategorizer.applyCategoryResult(ledgerRecord, result);
        return ledgerRecord;
    }
}
