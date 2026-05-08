package org.kumar.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ListIterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

import org.kumar.excel.util.LedgerRecord;
import org.kumar.excel.util.LedgerStats;
import org.kumar.excel.ReadLedgerFile;

/**
 * Rules engine that applies the FIRST matching rule.
 *
 * Enhancements:
 *  - Reads RuleNo from rules.json (or defaults to array index + 1)
 *  - Exposes executeRulesWithRuleNo() to know which rule fired
 */
public class MyRulesEngine {

    private static final Logger LOGGER = LogManager.getLogger(MyRulesEngine.class);

    static String rulesFile = "rules.json";
    static Rule[] allRules = null;

    public static JSONObject jConfig = null;

    /**
     * Small DTO: result record + rule number that matched (0 if none).
     */
    public static class RuleMatch {
        public final LedgerRecord record;
        public final int ruleNo;

        public RuleMatch(LedgerRecord record, int ruleNo) {
            this.record = record;
            this.ruleNo = ruleNo;
        }
    }

    /**
     * Load the rules from a config file
     */
    public static void init() {
        LOGGER.info("loading the rules from " + rulesFile);
        try {
            InputStream is = MyRulesEngine.class.getClassLoader().getResourceAsStream(rulesFile);
            if (is == null) {
                throw new IOException("Could not find rules file on classpath: " + rulesFile);
            }

            Object o = new JSONParser().parse(new InputStreamReader(is, "UTF-8"));
            jConfig = (JSONObject) o;

            JSONArray fields = (JSONArray) jConfig.get("Fields");
            LOGGER.info("Fields: " + fields);

            JSONArray rules = (JSONArray) jConfig.get("Rules");
            allRules = new Rule[rules.size()];

            for (int i = 0; i < rules.size(); i++) {
                JSONObject ruleJson = (JSONObject) (rules.get(i));

                // ✅ Read RuleNo if present, else default to i+1
                int ruleNo = i + 1;
                Object ruleNoObj = ruleJson.get("RuleNo");
                if (ruleNoObj instanceof Number) {
                    ruleNo = ((Number) ruleNoObj).intValue();
                } else if (ruleNoObj != null) {
                    try {
                        ruleNo = Integer.parseInt(ruleNoObj.toString().trim());
                    } catch (Exception ignore) {
                        ruleNo = i + 1;
                    }
                }

                // conditions
                JSONArray conditions = (JSONArray) (ruleJson.get("Conditions"));
                SimpleCondition[] scons = new SimpleCondition[conditions.size()];

                for (int k = 0; k < conditions.size(); k++) {
                    JSONObject condition = (JSONObject) (conditions.get(k));
                    String field = (String) (condition.get("field"));
                    String operator = (String) (condition.get("operator"));

                    // value OR values_in[]
                    String value = (String) (condition.get("value"));
                    JSONArray jsonArrayValues = (JSONArray) (condition.get("values_in"));

                    String[] values = null;
                    if (jsonArrayValues != null && !jsonArrayValues.isEmpty()) {
                        values = new String[jsonArrayValues.size()];
                        for (int x = 0; x < jsonArrayValues.size(); x++) {
                            values[x] = (String) (jsonArrayValues.get(x));
                        }
                    } else if (value != null) {
                        values = new String[] { value };
                    } else {
                        values = new String[] { "" };
                    }

                    scons[k] = new SimpleCondition(field, operator, values);
                }

                // decision
                JSONObject decision = (JSONObject) (ruleJson.get("Decision"));
                Map<String, String> ruleOutput = new HashMap<String, String>();
                Set<String> keys = (Set<String>) (decision.keySet());
                for (String key : keys) {
                    ruleOutput.put(key, (String) (decision.get(key)));
                }

                RuleCondition rc = new RuleCondition(scons);
                RuleDecision rd = new RuleDecision(ruleOutput);

                allRules[i] = new Rule(ruleNo, rc, rd);
            }

        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (ParseException e2) {
            e2.printStackTrace();
        }
    }

    /**
     * Execute rules for a given record.
     * @return updated record, or null if none matched
     */
    public LedgerRecord executeRules(LedgerRecord rec) {
        RuleMatch rm = executeRulesWithRuleNo(rec);
        return rm == null ? null : rm.record;
    }

    /**
     * Execute rules for a given record and also return which rule matched.
     * @return RuleMatch(record, ruleNo). If no rule matched, returns RuleMatch(null, 0).
     */
    public RuleMatch executeRulesWithRuleNo(LedgerRecord rec) {
        if (rec == null) return new RuleMatch(null, 0);

        for (int i = 0; i < allRules.length; i++) {
            Rule rule = allRules[i];
            LedgerRecord newRec = rule.executeRule(rec);
            if (newRec != null) {
                return new RuleMatch(newRec, rule.getRuleNo());
            }
        }
        return new RuleMatch(null, 0);
    }

    /**
     * Apply rules to a list
     */
    public List<LedgerRecord> executeRules(List<LedgerRecord> records) {
        if (records == null || records.isEmpty())
            return null;

        List<LedgerRecord> newList = new ArrayList<LedgerRecord>(records.size());
        ListIterator<LedgerRecord> iter = records.listIterator(0);

        while (iter.hasNext()) {
            LedgerRecord rec = (LedgerRecord) (iter.next());
            LedgerRecord newRec = executeRules(rec);
            if (newRec != null) {
                newList.add(newRec);
            } else {
                newList.add(rec);
            }
        }
        return newList;
    }

    public static void main(String[] args) {
        MyRulesEngine.init();
        LOGGER.info("loaded " + MyRulesEngine.allRules.length);
        for (int i = 0; i < MyRulesEngine.allRules.length; i++) {
            LOGGER.info(MyRulesEngine.allRules[i]);
        }

        ReadLedgerFile reader = new ReadLedgerFile();
        String ledgerFile = "D:\\fin_docs\\Consolidated Ledger v2_mar2025.xlsx";
        String sheet = "Runbook";
        try {
            List<LedgerRecord> records = reader.readFile2(ledgerFile, sheet);
            MyRulesEngine engine = new MyRulesEngine();
            List<LedgerRecord> newRecords = engine.executeRules(records);

            LedgerStats oldStats = new LedgerStats();
            oldStats.buildStats(records);

            LedgerStats newStats = new LedgerStats();
            newStats.buildStats(newRecords);

            LOGGER.info(" old stats " + oldStats + " new stats " + newStats);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
