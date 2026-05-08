package org.kumar.json;

import java.util.Arrays;

import org.kumar.excel.util.LedgerRecord;

/**
 * A single condition in a rule.
 *
 * Notes / fixes:
 * - Operator matching is now case-insensitive and tolerant of extra spaces.
 * - Description contains/starts-with matching is case-insensitive (more robust across banks).
 * - Added support for operator "starts with" for Description and Acct_type.
 * - Amount operators are case-insensitive: "Less Than", "less than", etc.
 */
public class SimpleCondition {

    private final String _field;
    private final String _operator;
    private final String[] _values;

    public SimpleCondition(String field, String operator, String[] values) {
        _field = field;
        _operator = operator;
        _values = Arrays.copyOf(values, values.length);
    }

    public String getField() { return _field; }
    public String getOperator() { return _operator; }
    public String[] getValues() { return _values; }

    @Override
    public String toString() {
        return (_field + " " + _operator + " " + Arrays.toString(_values));
    }

    private static String normOp(String op) {
        return (op == null) ? "" : op.trim().toLowerCase();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static boolean startsWithIgnoreCase(String haystack, String prefix) {
        if (haystack == null || prefix == null) return false;
        return haystack.toLowerCase().startsWith(prefix.toLowerCase());
    }

    public boolean applyCondition(LedgerRecord rec) {
        if (rec == null) return false;

        final String field = (_field == null) ? "" : _field.trim();
        final String op = normOp(_operator);

        if (field.equals("Description")) {
            final String desc = rec.getDescription();
            if (desc == null) return false;

            if (op.equals("equals")) {
                for (String s : _values) {
                    if (s != null && s.equalsIgnoreCase(desc)) return true;
                }
                return false;
            } else if (op.equals("contains")) {
                for (String s : _values) {
                    if (containsIgnoreCase(desc, s)) return true;
                }
                return false;
            } else if (op.equals("starts with") || op.equals("startswith")) {
                for (String s : _values) {
                    if (startsWithIgnoreCase(desc, s)) return true;
                }
                return false;
            }
            return false;

        } else if (field.equals("Acct_type")) {
            // NOTE: your LedgerRecord uses getAccount() for the account string.
            // If you truly mean "account type", consider adding a LedgerRecord.getAcctType().
            final String act = rec.getAccount();
            if (act == null) return false;

            if (op.equals("equals")) {
                for (String s : _values) {
                    if (s != null && s.equalsIgnoreCase(act)) return true;
                }
                return false;
            } else if (op.equals("contains")) {
                for (String s : _values) {
                    if (containsIgnoreCase(act, s)) return true;
                }
                return false;
            } else if (op.equals("starts with") || op.equals("startswith")) {
                for (String s : _values) {
                    if (startsWithIgnoreCase(act, s)) return true;
                }
                return false;
            }
            return false;

        } else if (field.equals("Amount")) {
            if (_values == null || _values.length == 0 || _values[0] == null) return false;

            final double rhs;
            try {
                rhs = Double.parseDouble(_values[0].trim());
            } catch (Exception e) {
                return false;
            }

            final double lhs = rec.getAmount();
            // tolerate different casings / spacing
            if (op.equals("greater than") || op.equals("greaterthan") || op.equals(">")) return lhs > rhs;
            if (op.equals("less than") || op.equals("lessthan") || op.equals("<")) return lhs < rhs;
            if (op.equals("equals") || op.equals("==")) return lhs == rhs;

            return false;
        }

        return false;
    }
}
