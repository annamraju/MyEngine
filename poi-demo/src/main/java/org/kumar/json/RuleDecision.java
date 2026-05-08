package org.kumar.json;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.kumar.excel.util.LedgerRecord;

public class RuleDecision {

    private Map<String, String> _output = null;

    public RuleDecision(Map<String, String> output) {
        _output = output;
    }

    public RuleDecision(String field, String value) {
        _output = new HashMap<String, String>();
        _output.put(field, value);
    }

    /**
     * Apply decision outputs onto the ledger record.
     *
     * Fixes:
     * - Accepts both "SubCategory3" and legacy misspelling "SubCatetory3".
     * - Also tolerates "Subcategory2/3/4" (case-insensitive) from older JSON.
     */
    public void applyDecision(LedgerRecord rec) {
        if (rec == null || _output == null) return;

        for (Map.Entry<String, String> entry : _output.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null) continue;

            if (key.equalsIgnoreCase("Category")) {
                rec.setCategory(value);
            } else if (key.equalsIgnoreCase("SubCategory1")) {
                rec.setSubCategory1(value);
            } else if (key.equalsIgnoreCase("SubCategory2") || key.equalsIgnoreCase("Subcategory2")) {
                rec.setSubCategory2(value);
            } else if (key.equalsIgnoreCase("SubCategory3") || key.equalsIgnoreCase("Subcategory3") || key.equalsIgnoreCase("SubCatetory3")) {
                rec.setSubCategory3(value);
            } else if (key.equalsIgnoreCase("SubCategory4") || key.equalsIgnoreCase("Subcategory4")) {
                rec.setSubCategory4(value);
            }
        }
    }

    @Override
    public String toString() {
        if (_output == null || _output.isEmpty()) return "";
        Set<String> keys = _output.keySet();
        Iterator<String> iter = keys.iterator();
        StringBuffer buf = new StringBuffer();
        buf.append(" Set ");
        int count = 0;
        while (iter.hasNext()) {
            String field = iter.next();
            String value = _output.get(field);
            if (count != 0) buf.append(" and ");
            buf.append(field + " to " + value);
            count++;
        }
        return buf.toString();
    }
}
