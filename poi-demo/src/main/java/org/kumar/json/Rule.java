package org.kumar.json;

import org.kumar.excel.util.LedgerRecord;

/**
 * One rule = conditions + decision (+ RuleNo for traceability)
 */
public class Rule {

    private final int ruleNo;
    private final RuleCondition rc;
    private final RuleDecision rd;

    public Rule(int ruleNo, RuleCondition rc, RuleDecision rd) {
        this.ruleNo = ruleNo;
        this.rc = rc;
        this.rd = rd;
    }

    public int getRuleNo() {
        return ruleNo;
    }

    /**
     * Executes this rule on a record.
     * @return a NEW LedgerRecord with the decision applied, or null if condition didn't match
     */
    public LedgerRecord executeRule(LedgerRecord rec) {
        if (rec == null) return null;

        if (rc.applyCondition(rec)) {
            LedgerRecord newRec = new LedgerRecord(rec);
            rd.applyDecision(newRec);

            // Best-effort: stamp rule number on the record if LedgerRecord supports it.
            stampRuleNo(newRec, ruleNo);

            return newRec;
        }
        return null;
    }

    /**
     * Try to set rule number on LedgerRecord without hard dependency on a specific API.
     * If LedgerRecord doesn't have the method, we silently ignore.
     */
    private static void stampRuleNo(LedgerRecord rec, int ruleNo) {
        if (rec == null) return;
        try {
            rec.getClass().getMethod("setRuleNo", int.class).invoke(rec, ruleNo);
            return;
        } catch (Exception ignore) {}
        try {
            rec.getClass().getMethod("setRuleNumber", int.class).invoke(rec, ruleNo);
            return;
        } catch (Exception ignore) {}
        try {
            rec.getClass().getMethod("setRuleId", int.class).invoke(rec, ruleNo);
        } catch (Exception ignore) {}
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("RuleNo=").append(ruleNo).append(" :: ");
        buf.append("Condition -> ").append(rc.toString());
        buf.append(" then Outcome -> ").append(rd.toString());
        return buf.toString();
    }
}
