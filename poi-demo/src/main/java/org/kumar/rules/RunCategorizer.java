package org.kumar.rules;

import org.kumar.excel.ReadLedgerFile;
import org.kumar.excel.util.LedgerRecord;
import org.kumar.rules.LedgerRecordCategorizer.CategorizationResult;
import org.kumar.rules.LedgerRecordCategorizer.CategoryResult;
import org.kumar.rules.LedgerRecordCategorizer.Engine;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RunCategorizer {

    // ---- OPTION FLAGS ----
    private static final boolean SAVE_OUTPUT = true;  // <-- set false if you don't want a file
    private static final Path OUTPUT_DIR = Path.of("D:\\fin_docs\\output");

    public static void main(String[] args) throws Exception {
        String merchantMapResource = getArgOrDefault(args, 0, "merchant_map.json");
        String rulesResource = getArgOrDefault(args, 1, "rules.json");
        String checkRulesResource = getArgOrDefault(args, 2, "check_rules.json");

        System.out.println("Using resources: "
                + "merchant_map=" + merchantMapResource
                + ", rules=" + rulesResource
                + ", check_rules=" + checkRulesResource);

        Engine engine = LedgerRecordCategorizer.load(
                merchantMapResource,
                rulesResource,
                checkRulesResource
        );

        // you already have this from ReadLedgerFile.readFile(...)
//        String ledgerFile = "D:\\fin_docs\\updated_ledger_01192026_almost_final.xlsx";
        //String ledgerFile = "D:\\fin_docs\\SampleDebug.xlsx";
        String ledgerFile = "D:\\fin_docs\\Ledger_pre_categorized.xlsx";
        //String ledgerFile = "D:\\fin_docs\\test_trans.xlsx";
        String sheetName  = "Runbook";
  	  	ReadLedgerFile fileReader =  new ReadLedgerFile();
  	  	List<LedgerRecord> ledger = fileReader.readFile(ledgerFile, sheetName);  
  	  	
        List<CategorizationResult> results = LedgerRecordCategorizer.categorizeAll(engine, ledger);

        int count = 0;
        for(int i = 0; i < results.size(); i++) {
        	CategoryResult x =  (results.get(i)).categoryResult;
        	if(x == null || isBlank(x.r_category)) count++;
        }
        // show some stats as to how many were uncategorized
        System.out.println(count + " were STILL uncategorized out of " + ledger.size());
        
        // ---- Save categorization audit output (CSV) ----
        if (SAVE_OUTPUT) {
            Path outFile = buildOutputFile("categorization_output", "csv");
            saveCategorizationCsv(results, outFile);
            System.out.println("Saved categorization output to: " + outFile.toAbsolutePath());
        }

  	  	printStats(results);
  	  	/*
        // Example audit: rule matched but category still blank
        for (CategorizationResult r : results) {
            if (r.ruleMatched && isBlank(r.record.getCategory())) {
                System.out.println("Rule " + r.ruleNoMatched
                        + " matched but category blank. Merchant=" + r.merchant
                        + " Desc=" + r.record.getDescription());
            }
        }
        */
    }

    public static void printStats(List<CategorizationResult> results) {
    	if(results == null || results.size() ==0) return;
    	Map<String, Integer> stats = new HashMap<String, Integer>();
    	for(CategorizationResult res : results) {
    		CategoryResult x =  res.categoryResult;
    		
    		if(x != null && !isBlank(x.r_category)) continue;
    		LedgerRecord rec = res.record;
    		StringBuffer buf =  new StringBuffer();
    		buf.append(isBlank(rec.getCategory()) ? "" : rec.getCategory());
    		buf.append("|");    		
    		buf.append(isBlank(rec.getSub1()) ? "" : rec.getSub1());
    		//buf.append("|");
    		//buf.append(isBlank(rec.getSub2()) ? "" : rec.getSub2());
    		if(stats.containsKey(buf.toString())) {
    			Integer value = stats.get(buf.toString());
    			value =  value + 1;
    			stats.put(buf.toString(), value);
    		}else {
    			stats.put(buf.toString(), 1);
    		}    				
    	}
    	
    	// now print
    	Map<String, Integer> sorted =
    		    stats.entrySet()
    		       .stream()
    		       .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
    		       .collect(Collectors.toMap(
    		           Map.Entry::getKey,
    		           Map.Entry::getValue,
    		           (e1, e2) -> e1,
    		           LinkedHashMap::new
    		       ));

    		System.out.println(sorted);    	
    }

    /*
    public static void printStats(List<LedgerRecord> records) {
    	if(records == null || records.size() ==0) return;
    	Map<String, Integer> stats = new HashMap<String, Integer>();
    	for(LedgerRecord rec : records) {
    		StringBuffer buf =  new StringBuffer();
    		buf.append(isBlank(rec.getCategory()) ? "" : rec.getCategory());
    		buf.append("|");    		
    		buf.append(isBlank(rec.getSub1()) ? "" : rec.getSub1());
    		//buf.append("|");
    		//buf.append(isBlank(rec.getSub2()) ? "" : rec.getSub2());
    		if(stats.containsKey(buf.toString())) {
    			Integer value = stats.get(buf.toString());
    			value =  value + 1;
    			stats.put(buf.toString(), value);
    		}else {
    			stats.put(buf.toString(), 1);
    		}    				
    	}
    	
    	// now print
    	Map<String, Integer> sorted =
    		    stats.entrySet()
    		       .stream()
    		       .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
    		       .collect(Collectors.toMap(
    		           Map.Entry::getKey,
    		           Map.Entry::getValue,
    		           (e1, e2) -> e1,
    		           LinkedHashMap::new
    		       ));

    		System.out.println(sorted);    	
    }
    */
    
    private static Path buildOutputFile(String baseName, String ext) throws IOException {
        if (!Files.exists(OUTPUT_DIR)) {
            Files.createDirectories(OUTPUT_DIR);
        }
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return OUTPUT_DIR.resolve(baseName + "_" + ts + "." + ext);
    }

    private static void saveCategorizationCsv(List<CategorizationResult> results, Path outFile) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {

            // Header
            w.write(String.join(",",
                    "Account",
                    "TransDate",
                    "Description",
                    "Amount",
                    "MerchantID",
                    "Merchant",
                    "RuleMatched",
                    "RuleNoMatched",
                    "Original Category",
                    "Original Sub1",
                    "Original Sub2",
                    "Original Sub3",
                    "Original Sub4",
                    "Category",
                    "Sub1",
                    "Sub2",
                    "Sub3",
                    "Sub4",
                    "dif cate",
                    "diff sub1",
                    "diff sub2",
                    "diff sub3",
                    "diff sub4"
            ));
            w.newLine();

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

            for (CategorizationResult r : results) {
                LedgerRecord lr = r.record;

                String account = csv(lr.getAccount());
                String transDate = csv(lr.getTransDate() == null ? "" : sdf.format(lr.getTransDate()));
                String desc = csv(lr.getDescription());
                String amount = String.valueOf(lr.getAmount());
                String merchantID = csv(r.merchantID);
                String merchant = csv(r.merchant);
                String ruleMatched = String.valueOf(r.ruleMatched);
                String ruleNo = (r.ruleNoMatched == null) ? "" : String.valueOf(r.ruleNoMatched);

                String o_cat = csv(lr.getCategory());
                String o_sub1 = csv(lr.getSub1());
                String o_sub2 = csv(lr.getSub2());
                String o_sub3 = csv(lr.getSub3());
                String o_sub4 = csv(lr.getSub4());

                CategoryResult res =  r.categoryResult;
                String cat = csv( res != null ? res.r_category : "");
                String sub1 = csv(res != null ? res.r_sub1 : "");
                String sub2 = csv(res != null ? res.r_sub2 : "");
                String sub3 = csv(res != null ? res.r_sub3 : "");
                String sub4 = csv(res != null ? res.r_sub4 : "");

                w.write(String.join(",",
                        account,
                        transDate,
                        desc,
                        amount,
                        merchantID,
                        merchant,
                        ruleMatched,
                        ruleNo,
                        o_cat,
                        o_sub1,
                        o_sub2,
                        o_sub3,
                        o_sub4,
                        cat,
                        sub1,
                        sub2,
                        sub3,
                        sub4,
                        (o_cat.equalsIgnoreCase(cat)) ? "TRUE" : "FALSE"  ,
                        (o_sub1.equalsIgnoreCase(sub1)) ? "TRUE" : "FALSE",
                        (o_sub2.equalsIgnoreCase(sub2)) ? "TRUE" : "FALSE",
                        (o_sub3.equalsIgnoreCase(sub3)) ? "TRUE" : "FALSE",
                        (o_sub4.equalsIgnoreCase(sub4)) ? "TRUE" : "FALSE"
                        		
                ));
                w.newLine();
            }
        }
    }

    /**
     * CSV escape:
     * - wrap in quotes if contains comma/quote/newline
     * - escape quotes by doubling
     */
    private static String csv(String s) {
        if (s == null) return "";
        String v = s;
        boolean needsQuotes = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (v.contains("\"")) v = v.replace("\"", "\"\"");
        return needsQuotes ? "\"" + v + "\"" : v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim());
    }

    private static String getArgOrDefault(String[] args, int idx, String defaultVal) {
        if (args == null || idx < 0 || idx >= args.length) return defaultVal;
        String val = args[idx];
        if (isBlank(val)) return defaultVal;
        return val.trim();
    }
}
