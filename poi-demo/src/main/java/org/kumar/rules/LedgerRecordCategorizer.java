package org.kumar.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.kumar.excel.util.LedgerRecord;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

public class LedgerRecordCategorizer {
	private static final Logger LOGGER = LogManager.getLogger("LedgerCategorization");

    // Macro tokens (can be used in merchant_map.json defaults OR rules.json actions)
    private static final String MACRO_ACCOUNT = "$ACCOUNT";
    private static final String MACRO_MERCHANT = "$MERCHANT";   // canonical merchant name
    private static final String MACRO_MERCHANT_ID = "$MERCHANT_ID"; // merchantId from merchant_map.json (optional)
    private static final String MACRO_PRUNED_MERCHANT = "$PRUNED_MERCHANT";
    private static final String MACRO_BLANK = "$BLANK"; // explicit clear token for rule actions
    private static final String EXPLICIT_BLANK_SENTINEL = "__EXPLICIT_BLANK__";

    // NEW: macros based on the *normalized* description (same normalization used for rule matching)
    private static final String MACRO_DESC_NORM = "$DESC_NORM";
    private static final String MACRO_DESC_NORM_PREFIX = "$DESC_NORM_PREFIX"; // usage: $DESC_NORM_PREFIX(n[,maxLen])
    private static final String MACRO_DESC_NORM_REGEX = "$DESC_NORM_REGEX";   // usage: $DESC_NORM_REGEX(<regex-with-capture-group>)

    // ------------ Public API ------------
    /** Load merchant_map.json + rules.json + optional check_rules.json */
    public static Engine load(String merchantMapResource, String rulesResource, String checkRulesResource) throws IOException {

        ObjectMapper om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ClassLoader cl = LedgerRecordCategorizer.class.getClassLoader();

        MerchantMap merchantMap;
        RuleSet ruleSet;
        CheckRuleSet checkRuleSet = null;

        try (InputStream mmIs = cl.getResourceAsStream(merchantMapResource)) {
            if (mmIs == null) {
                throw new FileNotFoundException("Resource not found: " + merchantMapResource);
            }
            merchantMap = om.readValue(mmIs, MerchantMap.class);
        }

        try (InputStream rulesIs = cl.getResourceAsStream(rulesResource)) {
            if (rulesIs == null) {
                throw new FileNotFoundException("Resource not found: " + rulesResource);
            }
            ruleSet = om.readValue(rulesIs, RuleSet.class);
        }

        if (checkRulesResource != null && !checkRulesResource.isBlank()) {
            try (InputStream chkIs = cl.getResourceAsStream(checkRulesResource)) {
                if (chkIs == null) {
                    throw new FileNotFoundException("Resource not found: " + checkRulesResource);
                }
                checkRuleSet = om.readValue(chkIs, CheckRuleSet.class);
            }
        }

        LOGGER.info("Loaded the three json files");      
        return new Engine(
                new MerchantNormalizer(merchantMap),
                ruleSet,
                checkRuleSet,
                ZoneId.of("America/New_York")
        );
    } 
    /* This is the old one
	public static Engine load(String merchantMapResource, String rulesResource) throws IOException {

	    ObjectMapper om = new ObjectMapper()
	            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	    ClassLoader cl = LedgerRecordCategorizer.class.getClassLoader();

	    MerchantMap merchantMap;
	    RuleSet ruleSet;

	    try (InputStream mmIs = cl.getResourceAsStream(merchantMapResource)) {
	        if (mmIs == null) {
	            throw new FileNotFoundException("Resource not found: " + merchantMapResource);
	        }
	        merchantMap = om.readValue(mmIs, MerchantMap.class);
	    }

	    try (InputStream rulesIs = cl.getResourceAsStream(rulesResource)) {
	        if (rulesIs == null) {
	            throw new FileNotFoundException("Resource not found: " + rulesResource);
	        }
	        ruleSet = om.readValue(rulesIs, RuleSet.class);
	    }

	    return new Engine(
	            new MerchantNormalizer(merchantMap),
	            ruleSet,
	            ZoneId.of("America/New_York")
	    );
	}
	*/

    /** Process one record and write category/sub1..sub4 back into the LedgerRecord. */
    public static CategorizationResult categorizeOne(Engine engine, LedgerRecord r) {
        return engine.process(r);
    }

    /** Process all. */
    public static List<CategorizationResult> categorizeAll(Engine engine, List<LedgerRecord> ledger) {
    	//LOGGER.info("about to categorize the ledger");
        List<CategorizationResult> results = new ArrayList<>();
        for (LedgerRecord r : ledger) {
            results.add(engine.process(r));
        }
        return results;
    }

    public static class CategoryResult{
    	public String r_category;
    	public String r_sub1;
    	public String r_sub2;
    	public String r_sub3;
    	public String r_sub4;
    }
    
    // ------------ Result (so you get merchant + ruleNo without modifying LedgerRecord) ------------
    public static class CategorizationResult {
        public final LedgerRecord record;
        public final String merchant;
        public final String merchantID;
        public final boolean ruleMatched;
        public final Integer ruleNoMatched;
        public CategoryResult categoryResult;
        
        public CategorizationResult(LedgerRecord record, String merchant, String merchantID, boolean ruleMatched, Integer ruleNoMatched, CategoryResult res) {
            this.record = record;
            this.merchant = merchant;
            this.merchantID = merchantID;
            this.ruleMatched = ruleMatched;
            this.ruleNoMatched = ruleNoMatched;
            this.categoryResult = res;
        }
    }

    // ------------ Engine ------------
    public static class Engine {
        private final MerchantNormalizer normalizer;
        private final List<Rule> rulesOrdered;
        private final boolean defaultStopOnMatch;
        private final ZoneId zoneId;
        
        // Check rules (3rd JSON): account+checkNo -> Actions
        private final Map<String, Actions> checkActionsByKey;
        private static final int CHECK_RULE_OFFSET = 1_000_000;

        public Engine(MerchantNormalizer normalizer, RuleSet ruleSet, CheckRuleSet checkRuleSet, ZoneId zoneId) {
            this.normalizer = normalizer;
            this.zoneId = zoneId;

            this.defaultStopOnMatch = (ruleSet.engine != null && ruleSet.engine.defaultStopOnMatch != null)
                    ? ruleSet.engine.defaultStopOnMatch : true;

            List<Rule> enabled = new ArrayList<>();
            if (ruleSet.rules != null) {
                for (Rule r : ruleSet.rules) {
                    if (r != null && Boolean.TRUE.equals(r.enabled)) enabled.add(r);
                }
            }
            enabled.sort(Comparator
                    .comparingInt((Rule r) -> r.priority == null ? Integer.MAX_VALUE : r.priority)
                    .thenComparingInt(r -> r.ruleNo == null ? Integer.MAX_VALUE : r.ruleNo));
            this.rulesOrdered = Collections.unmodifiableList(enabled);

            // Build check lookup map
            Map<String, Actions> chk = new HashMap<>();
            if (checkRuleSet != null && checkRuleSet.checks != null) {
                for (CheckRule cr : checkRuleSet.checks) {
                    if (cr == null || !Boolean.TRUE.equals(cr.enabled)) continue;
                    if (cr.checkNo == null) continue;
                    String acc = str(cr.account).trim().toUpperCase(Locale.ROOT);
                    if (acc.isEmpty()) acc = "*";
                    chk.put(buildCheckKey(acc, cr.checkNo), cr.then);
                }
            }
            this.checkActionsByKey = Collections.unmodifiableMap(chk);
        }
        
        private boolean shouldSkipCheckRuleLookup(String description) {
            String d = normalizeDescForCheck(description);
            if (d == null) return false;

            return d.contains("OVERDRAFT ITEM FEE")
                    || d.contains("OVERDRAFT FEE")
                    || d.contains("NSF FEE")
                    || d.contains("RETURNED ITEM FEE")
                    || d.contains("INSUFFICIENT FUNDS FEE");
        }

        public CategorizationResult process(LedgerRecord r) {
        	//LOGGER.info("got this record to categorize" + r);
            // 1) Check rules take highest priority (account-specific "CHECK # NNN")
            Integer checkNo = extractCheckNo(r.getDescription());
            if (checkNo != null && !shouldSkipCheckRuleLookup(r.getDescription())) {
            	//LOGGER.info("this is a check related transaction");
                String accKey = str(r.getAccount()).trim().toUpperCase(Locale.ROOT);
                Actions chkActions = checkActionsByKey.get(buildCheckKey(accKey, checkNo));
                if (chkActions == null) {
                    chkActions = checkActionsByKey.get(buildCheckKey("*", checkNo));
                }
                if (chkActions != null) {
                    CategoryResult chkRes = applyActions(r, "CHECK", null, chkActions);
                    chkRes = materializeExplicitBlanks(chkRes);
                    Integer chkRuleNo = CHECK_RULE_OFFSET + checkNo;

                    // Do NOT fall through to other rules or merchant defaults
                    r.setMerchantId("CHECK");
                    r.setRuleNo(chkRuleNo);
                    //TODO: writeBackToRecord(r, chkRes);
                    return new CategorizationResult(r, "CHECK", "CHECK", true, chkRuleNo, chkRes);
                }
            }

            // Merchant from description
            MerchantHit mh = normalizer.match(r.getDescription());
            String merchant = (mh != null) ? mh.canonicalName : null;
            String merchantID = (mh != null) ? mh.merchantId : null;
            
            //LOGGER.info("after mornalizer.match " + merchant);
            boolean matchedAny = false;
            Integer matchedRuleNo = null;

            CategoryResult res =  null;
            for (Rule rule : rulesOrdered) {
            	//if(rule.ruleNo == 9936)
            	//	LOGGER.info("checking on rule " + rule);
                if (matchesAll(r, merchant, rule.when)) {
                	//LOGGER.info("it matched this rule " + rule);
                    matchedAny = true;
                    matchedRuleNo = rule.ruleNo;

                    res =  applyActions(r, merchant, mh, rule.then);

                    boolean stop = (rule.stopOnMatch != null) ? rule.stopOnMatch : defaultStopOnMatch;
                    if (stop) break;
                }
            }
            
            /*
             * 
             * {
  "merchantId": "SOME_MERCHANT",
  "canonicalName": "Some Merchant",
  "defaults": {
    "base": {
      "category": "Internal",
      "sub1": "Transfer"
    },
    "byAmountSign": {
      "negative": {
        "category": "Expense",
        "sub1": "Transfer Out"
      },
      "positive": {
        "category": "Income",
        "sub1": "Transfer In"
      }
    }
  },
  "match": {
    "patterns": [
      "\\bSOME\\s+MERCHANT\\b"
    ]
  }
}
             * 
             * 
             * 
             * 
             * 
             * 
             */

            // Apply merchant defaults only if category/sub1..sub4 are blank
            if (mh != null) {

                // Start with legacy defaults (backward compatible)
                String dCat = mh.defaultCategory;
                String dSub1 = mh.defaultSub1;
                String dSub2 = mh.defaultSub2;
                String dSub3 = mh.defaultSub3;
                String dSub4 = mh.defaultSub4;

                // Prefer v3 defaults.base (if present)
                if (mh.defaults != null && mh.defaults.base != null) {
                    DefaultBlock b = mh.defaults.base;
                    if (!isBlank(b.category)) dCat = b.category;
                    if (!isBlank(b.sub1))     dSub1 = b.sub1;
                    if (!isBlank(b.sub2))     dSub2 = b.sub2;
                    if (!isBlank(b.sub3))     dSub3 = b.sub3;
                    if (!isBlank(b.sub4))     dSub4 = b.sub4;
                }

                // Override via v3 defaults.byAmountSign (if present)
                if (mh.defaults != null && mh.defaults.byAmountSign != null) {
                    double amt = toDouble(r.getAmount());
                    DefaultBlock blk = null;

                    if (amt < 0) {
                        blk = mh.defaults.byAmountSign.negative;
                    } else if (amt > 0) {
                        blk = mh.defaults.byAmountSign.positive;
                    }

                    if (blk != null) {
                        if (!isBlank(blk.category)) dCat = blk.category;
                        if (!isBlank(blk.sub1))     dSub1 = blk.sub1;
                        if (!isBlank(blk.sub2))     dSub2 = blk.sub2;
                        if (!isBlank(blk.sub3))     dSub3 = blk.sub3;
                        if (!isBlank(blk.sub4))     dSub4 = blk.sub4;
                    }
                }

                // Apply only where blank (existing behavior)
                if ((res == null || isBlank(res.r_category)) && !isBlank(dCat)) {
                    if (res == null) res = new CategoryResult();
                    res.r_category = resolveMacro(dCat, r, merchant, mh);
                }
                if ((res == null || isBlank(res.r_sub1)) && !isBlank(dSub1)) {
                    if (res == null) res = new CategoryResult();
                    res.r_sub1 = resolveMacro(dSub1, r, merchant, mh);
                }
                if ((res == null || isBlank(res.r_sub2)) && !isBlank(dSub2)) {
                    if (res == null) res = new CategoryResult();
                    res.r_sub2 = resolveMacro(dSub2, r, merchant, mh);
                }
                if ((res == null || isBlank(res.r_sub3)) && !isBlank(dSub3)) {
                    if (res == null) res = new CategoryResult();
                    res.r_sub3 = resolveMacro(dSub3, r, merchant, mh);
                }
                if ((res == null || isBlank(res.r_sub4)) && !isBlank(dSub4)) {
                    if (res == null) res = new CategoryResult();
                    res.r_sub4 = resolveMacro(dSub4, r, merchant, mh);
                }
            }
            r.setMerchant(merchant);
            r.setMerchantId(merchantID);
            r.setRuleNo(matchedRuleNo);
            
         // Auto-fill sub3 for Expense/Misc if still blank
/***** I dont want this now            
            if (res != null) {
                String cat = str(res.r_category);
                String sub1 = str(res.r_sub1);
                if ("EXPENSE".equalsIgnoreCase(cat.trim())
                        && "MISC".equalsIgnoreCase(sub1.trim())
                        && isBlank(res.r_sub3)) {
                    String dn = normalizer.normalizeDesc(r.getDescription());
                    // Example policy: first 8 tokens, max 80 chars
                    res.r_sub3 = firstTokens(dn, 8, 80);
                }
            }
**********/            
            res = materializeExplicitBlanks(res);
            //TODO: writeBackToRecord(r, res);
            return new CategorizationResult(r, merchant, merchantID, matchedAny, matchedRuleNo, res);
        }
        
        /**
         * Copy computed category/sub values back into the LedgerRecord.
         * Only non-blank values are written.
        */
        private void writeBackToRecord(LedgerRecord r, CategoryResult res) {
        	if (r == null || res == null) return;
        
        	if (!isBlank(res.r_category)) r.setCategory(res.r_category);
        	if (!isBlank(res.r_sub1))     r.setSubCategory1(res.r_sub1);
        	if (!isBlank(res.r_sub2))     r.setSubCategory2(res.r_sub2);
        	if (!isBlank(res.r_sub3))     r.setSubCategory3(res.r_sub3);
        	if (!isBlank(res.r_sub4))     r.setSubCategory4(res.r_sub4);
        }        

        // helper inside Engine (private static is ok too)
        private static String firstTokens(String s, int n, int maxLen) {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            String[] toks = s.split("\\s+");
            int take = Math.min(n, toks.length);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < take; i++) {
                if (i > 0) sb.append(' ');
                sb.append(toks[i]);
            }
            String out = sb.toString().trim();
            if (out.length() > maxLen) out = out.substring(0, maxLen).trim();
            return out.isEmpty() ? null : out;
        }
        
        private boolean matchesAll(LedgerRecord r, String merchant, List<Condition> conditions) {
            if (conditions == null || conditions.isEmpty()) return true;
            for (Condition c : conditions) {
                if (c == null) continue;
                if (!evalCondition(r, merchant, c)) return false;
            }
            return true;
        }

        private boolean evalCondition(LedgerRecord r, String merchant, Condition c) {
            String field = upper(c.field);
            String op = upper(c.op);

            Object fieldValue = null;
            switch(field){
            case "DESCRIPTION" : 
            		//fieldValue =  r.getDescription();
            		fieldValue =  normalizer.normalizeDesc(r.getDescription()); 
            		break;
            case "DESCRIPTION_RAW":
                // new behavior: original description, untouched
                fieldValue = r.getDescription();
                break;
                
            case "ACCOUNT" :
            	fieldValue = r.getAccount();
            	break;            	
            case "AMOUNT" :
            	fieldValue = r.getAmount();
            	break;
            case "CATEGORY":
            	fieldValue = r.getCategory();
            	break;
            case "SUB1" :
            	fieldValue = r.getSub1();
            	break;
            case "SUB2":
            	fieldValue = r.getSub2();
            	break;
            case "SUB3" :
            	fieldValue = r.getSub3();
            	break;
            case "SUB4" :
            	fieldValue =  r.getSub4();
            	break;
            case "MERCHANT" :
            	fieldValue = merchant;
            	break;
            case "DATE" :
            	fieldValue = toLocalDate(r.getTransDate());
            	break;
            default :
            	fieldValue = null;
            	break;
            }
            /*
            Object fieldValue = switch (field) {
                case "DESCRIPTION" -> r.getDescription();
                case "ACCOUNT" -> r.getAccount();
                case "AMOUNT" -> r.getAmount();
                case "CATEGORY" -> r.getCategory();
                case "SUB1" -> r.getSub1();
                case "SUB2" -> r.getSub2();
                case "SUB3" -> r.getSub3();
                case "SUB4" -> r.getSub4();
                case "MERCHANT" -> merchant;
                case "DATE" -> toLocalDate(r.getTransDate());
                default -> null;
            };
			*/
            if ("IS_BLANK".equals(op)) return isBlank(String.valueOf(fieldValue));
            if ("IS_NOT_BLANK".equals(op)) return !isBlank(String.valueOf(fieldValue));

            if ("EQ".equals(op)) {
                return Objects.equals(norm(fieldValue), norm(c.value));
            }
            if ("CONTAINS".equals(op)) {
                String hay = str(fieldValue).toUpperCase();
                String needle = str(c.value).toUpperCase();
                return hay.contains(needle);
            }
            if ("REGEX".equals(op)) {
                String s = str(fieldValue);
                if (s.isEmpty()) return false;
                Pattern p = Pattern.compile(String.valueOf(c.value), Pattern.CASE_INSENSITIVE);
                return p.matcher(s).find();
            }

            if (fieldValue instanceof Number || "AMOUNT".equals(field)) {
                double left = toDouble(fieldValue);
                double right = toDouble(c.value);
                switch(op) {
                case "GT" : return (left > right);
                case "GTE": return (left >= right);
                case "LT" : return (left < right);
                case "LTE" : return (left <= right);
                default : return false;
                }
                /*
                return switch (op) {
                    case "GT" -> left > right;
                    case "GTE" -> left >= right;
                    case "LT" -> left < right;
                    case "LTE" -> left <= right;
                    default -> false;
                };
                */
            }

            if (fieldValue instanceof LocalDate) {
                LocalDate left = (LocalDate) fieldValue;
                LocalDate right = parseDate(c.value);
                if (right == null) return false;
                switch (op) {
                case "BEFORE" : return (left.isBefore(right));
                case "AFTER" : return (left.isAfter(right));
                case "ON_OR_BEFORE" : return (!left.isAfter(right));
                case "ON_OR_AFTER" : return (!left.isBefore(right));
                default : return false;
                }

                /*
                return switch (op) {
                case "BEFORE" -> left.isBefore(right);
                case "AFTER" -> left.isAfter(right);
                case "ON_OR_BEFORE" -> !left.isAfter(right);
                case "ON_OR_AFTER" -> !left.isBefore(right);
                default -> false;
            	};
            	*/
            }

            return false;
        }

        private CategoryResult applyActions(LedgerRecord r, String merchant, MerchantHit mh, Actions a) {
            if (a == null) return null;
            CategoryResult res =  new CategoryResult();
            /*
            if (!isBlank(a.setCategory)) r.setCategory(a.setCategory);
            if (!isBlank(a.setSub1)) r.setSubCategory1(a.setSub1);
            if (!isBlank(a.setSub2)) r.setSubCategory2(a.setSub2);
            if (!isBlank(a.setSub3)) r.setSubCategory3(a.setSub3);
            if (!isBlank(a.setSub4)) r.setSubCategory4(a.setSub4);
            */
            res.r_category = resolveActionValue(a.setCategory, r, merchant, mh);
            res.r_sub1 = resolveActionValue(a.setSub1, r, merchant, mh);
            res.r_sub2 = resolveActionValue(a.setSub2, r, merchant, mh);
            res.r_sub3 = resolveActionValue(a.setSub3, r, merchant, mh);
            res.r_sub4 = resolveActionValue(a.setSub4, r, merchant, mh);
            return res;
        }

        private String resolveActionValue(String raw, LedgerRecord r, String merchant, MerchantHit mh) {
            if (isBlank(raw)) return null;
            if (MACRO_BLANK.equalsIgnoreCase(raw.trim())) return EXPLICIT_BLANK_SENTINEL;
            return resolveMacro(raw, r, merchant, mh);
        }

        private CategoryResult materializeExplicitBlanks(CategoryResult res) {
            if (res == null) return null;
            if (EXPLICIT_BLANK_SENTINEL.equals(res.r_category)) res.r_category = null;
            if (EXPLICIT_BLANK_SENTINEL.equals(res.r_sub1)) res.r_sub1 = null;
            if (EXPLICIT_BLANK_SENTINEL.equals(res.r_sub2)) res.r_sub2 = null;
            if (EXPLICIT_BLANK_SENTINEL.equals(res.r_sub3)) res.r_sub3 = null;
            if (EXPLICIT_BLANK_SENTINEL.equals(res.r_sub4)) res.r_sub4 = null;
            return res;
        }
        
        private String resolveMacro(String raw, LedgerRecord r, String merchant, MerchantHit mh) {
            if (raw == null) return null;

            // Support macros anywhere in the string (case-insensitive):
            //   $ACCOUNT      -> LedgerRecord.account
            //   $MERCHANT     -> matched merchant canonicalName
            //   $MERCHANT_ID  -> merchantId from merchant_map.json (if you decide to expose it later)
            //  $PRUNED_MERCHANT
            String out = raw;

            String account = str(r.getAccount()).trim();
            String merch = str(merchant).trim();
            String merchId = (mh != null) ? str(mh.merchantId).trim() : "";
            
            // Normalized description (same as rule matching uses)
            String descNorm = normalizer.normalizeDesc(r.getDescription()).trim();
            String prunedMerchant = firstPrunedAccountToken(descNorm);
            
            // Basic macros anywhere in the string (case-insensitive)
            out = out.replaceAll("(?i)\\Q" + MACRO_ACCOUNT + "\\E", java.util.regex.Matcher.quoteReplacement(account));
            out = out.replaceAll("(?i)\\Q" + MACRO_MERCHANT + "\\E", java.util.regex.Matcher.quoteReplacement(merch));
            out = out.replaceAll("(?i)\\Q" + MACRO_MERCHANT_ID + "\\E", java.util.regex.Matcher.quoteReplacement(merchId));
            out = out.replaceAll("(?i)\\Q" + MACRO_DESC_NORM + "\\E", java.util.regex.Matcher.quoteReplacement(descNorm));
            out = out.replaceAll("(?i)\\Q" + MACRO_PRUNED_MERCHANT + "\\E", java.util.regex.Matcher.quoteReplacement(prunedMerchant));

            // $DESC_NORM_PREFIX(n[,maxLen])  -> first n tokens of descNorm, optionally truncated to maxLen chars
            // Example: $DESC_NORM_PREFIX(6,60)
            java.util.regex.Matcher mPrefix = Pattern
                    .compile("(?i)\\Q" + MACRO_DESC_NORM_PREFIX + "\\E\\((\\d+)(?:\\s*,\\s*(\\d+))?\\)")
                    .matcher(out);
            while (mPrefix.find()) {
                int n = Integer.parseInt(mPrefix.group(1));
                int maxLen = (mPrefix.group(2) != null) ? Integer.parseInt(mPrefix.group(2)) : Integer.MAX_VALUE;

                String repl = "";
                if (!descNorm.isEmpty() && n > 0) {
                    String[] toks = descNorm.split("\\s+");
                    int take = Math.min(n, toks.length);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < take; i++) {
                        if (i > 0) sb.append(' ');
                        sb.append(toks[i]);
                    }
                    repl = sb.toString().trim();
                    if (repl.length() > maxLen) repl = repl.substring(0, maxLen).trim();
                }

                out = mPrefix.replaceFirst(java.util.regex.Matcher.quoteReplacement(repl));
                mPrefix = Pattern
                        .compile("(?i)\\Q" + MACRO_DESC_NORM_PREFIX + "\\E\\((\\d+)(?:\\s*,\\s*(\\d+))?\\)")
                        .matcher(out);
            }

            // $DESC_NORM_REGEX(<regex-with-capture-group>) -> group(1) match against descNorm
            // Example: $DESC_NORM_REGEX(^(USPS\\s+PO\\s+\\d+))
            java.util.regex.Matcher mRegex = Pattern
                    .compile("(?i)\\Q" + MACRO_DESC_NORM_REGEX + "\\E\\((.+)\\)")
                    .matcher(out);
            while (mRegex.find()) {
                String userRegex = mRegex.group(1);
                String repl = "";
                try {
                    Pattern p = Pattern.compile(userRegex, Pattern.CASE_INSENSITIVE);
                    java.util.regex.Matcher dm = p.matcher(descNorm);
                    if (dm.find() && dm.groupCount() >= 1) {
                        repl = str(dm.group(1)).trim();
                    }
                } catch (Exception ignored) {
                    // If regex is invalid, just replace with blank
                }

                out = mRegex.replaceFirst(java.util.regex.Matcher.quoteReplacement(repl));
                mRegex = Pattern
                        .compile("(?i)\\Q" + MACRO_DESC_NORM_REGEX + "\\E\\((.+)\\)")
                        .matcher(out);
            }
            
            out = out.replaceAll("(?i)\\Q" + MACRO_ACCOUNT + "\\E", java.util.regex.Matcher.quoteReplacement(account));
            out = out.replaceAll("(?i)\\Q" + MACRO_MERCHANT + "\\E", java.util.regex.Matcher.quoteReplacement(merch));
            out = out.replaceAll("(?i)\\Q" + MACRO_MERCHANT_ID + "\\E", java.util.regex.Matcher.quoteReplacement(merchId));

            out = out.trim();
            return out.isEmpty() ? null : out;
        }        

        private static String firstPrunedAccountToken(String normalizedDescription) {
            if (normalizedDescription == null || normalizedDescription.isBlank()) {
                return "";
            }

            Set<String> prefixesToDrop = new HashSet<>(Arrays.asList(
                    "SQ", "TST", "SR", "SP", "PP", "PAYPAL", "EB"
            ));

            String s = normalizedDescription
                    .replaceAll("[^A-Z0-9\\s]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            if (s.isEmpty()) return "";

            String[] toks = s.split("\\s+");
            for (String tok : toks) {
                if (tok == null || tok.isBlank()) continue;

                String t = tok.trim().toUpperCase(Locale.ROOT);

                // Drop processor/prefix tokens
                if (prefixesToDrop.contains(t)) continue;

                // Optional: ignore numeric-only tokens
                if (t.matches("\\d+")) continue;

                return t;
            }

            return "";
        }        
        private static String buildCheckKey(String accountUpper, int checkNo) {
            return accountUpper + "|" + checkNo;
        }

        private static Integer extractCheckNo(String description) {
            if (description == null) return null;

            // Remove BOM, NBSP, and most control chars (same spirit as MerchantNormalizer)
            String s = description
                    .replace("\uFEFF", "")
                    .replace('\u00A0', ' ')
                    .replaceAll("[\\p{C}&&[^\r\n\t]]", " ")
                    .trim();

            java.util.regex.Matcher m = Pattern.compile("(?i)\\bCHECK(?:\\s+IMAGE)?\\b(?:\\s*#)?\\s*(\\d+)\\b").matcher(s);
            if (m.find()) {
                try { return Integer.parseInt(m.group(1)); }
                catch (Exception ignored) { return null; }
            }
            return null;
        }
        
        public String normalizeDescForCheck(String s) {
            if (s == null) return "";
            
            s =    s.replace("Â", "")
            		.replace("\uFEFF", "")
                    .replace('\u00A0', ' ')
                    .replaceAll("[\\p{C}&&[^\r\n\t]]", " ")
                    .trim();
            String out = s;

            boolean doUpper = true;
            boolean stripPunct = true;
            boolean collapseWs = true;

            if (doUpper) out = out.toUpperCase(Locale.ROOT);
            if (stripPunct) out = out.replaceAll("[^A-Z0-9\\s]", " ");
            // Always split/join using whitespace for ignore-token filtering; collapse if configured
            String[] parts = out.trim().split("\\s+");
            if (parts.length > 0) {
                StringBuilder sb = new StringBuilder(out.length());
                for (String tok : parts) {
                    if (tok == null || tok.isBlank()) continue;
                    String t = doUpper ? tok : tok.toUpperCase(Locale.ROOT);
                    //if (ignoreTokens.contains(t)) continue;
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(doUpper ? tok : t);
                }
                out = sb.toString();
            }
            if (collapseWs) out = out.replaceAll("\\s+", " ").trim();
            return out;
        }
       

        private LocalDate toLocalDate(java.util.Date d) {
            if (d == null) return null;
            return d.toInstant().atZone(zoneId).toLocalDate();
        }
    }

    // ------------ Merchant Normalizer ------------
    public static class MerchantNormalizer {
        private final MerchantMap map;
        private final List<MerchantCompiled> compiled;
        private final Set<String> ignoreTokens;

        // Reasonable defaults (you can override/extend via merchant_map.json normalization.ignoreTokens)
        private static final Set<String> DEFAULT_IGNORE_TOKENS = new HashSet<>(Arrays.asList(
                "FL", "TAMPA", "ID", "WESLEY", "AMP"        		
               // "FL", "TAMPA", "ID", "THE", "FROM", "TO", "OF", "WESLEY", "STORE"
        ));

        public MerchantNormalizer(MerchantMap map) {
            this.map = map;
            List<MerchantCompiled> list = new ArrayList<>();

            if (map != null && map.merchants != null) {
                for (MerchantEntry e : map.merchants) {
                    if (e == null || e.match == null || e.match.patterns == null) continue;
                    List<Pattern> pats = new ArrayList<>();
                    for (String raw : e.match.patterns) {
                        if (raw == null || raw.isBlank()) continue;
                        pats.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
                    }
                    if (!pats.isEmpty()) list.add(new MerchantCompiled(e, pats));
                }
            }
            this.compiled = Collections.unmodifiableList(list);

            // Build ignore-token set (all compared in UPPERCASE)
            Set<String> it = new HashSet<>(DEFAULT_IGNORE_TOKENS);
            if (map != null && map.normalization != null && map.normalization.ignoreTokens != null) {
                for (String w : map.normalization.ignoreTokens) {
                    if (w == null || w.isBlank()) continue;
                    it.add(w.trim().toUpperCase(Locale.ROOT));
                }
            }
            this.ignoreTokens = Collections.unmodifiableSet(it);
        }

        /* This is the old match() logic
        public MerchantHit match(String description) {
            String normalized = normalizeDesc(description);
            //LOGGER.info("RAW: " + description);
            //LOGGER.info("NORM: " + normalized);
            for (MerchantCompiled mc : compiled) {
                for (Pattern p : mc.patterns) {
                    if (p.matcher(normalized).find()) {
                        MerchantEntry e = mc.entry;
                        // UPDATED: include defaultSub3
                        return new MerchantHit(e.merchantId, e.canonicalName, e.defaultCategory, e.defaultSub1, e.defaultSub2, e.defaultSub3, e.defaultSub4, e.defaults);
                    }
                }
            }
            return null;
        }
        */
       
        public MerchantHit match(String description) {
            String normalized = normalizeDesc(description);

            // 1) Try matching on normalized first (current behavior)
            MerchantHit hit = tryMatchAgainst(normalized);
            if (hit != null) return hit;

            // 2) Fallback: try matching on RAW (upper + control cleanup), without ignore-token removal
            String raw = rawForMerchantMatch(description);
            hit = tryMatchAgainst(raw);
            if (hit != null) return hit;

            String sig = merchantSignature(description);
            return tryMatchAgainst(sig);
        }

        private String merchantSignature(String s) {
            if (s == null) return "";

            String out = s.replace("Â", "")
                          .replace("\uFEFF", "")
                          .replace('\u00A0', ' ')
                          .replaceAll("[\\p{C}&&[^\r\n\t]]", " ")
                          .toUpperCase(Locale.ROOT)
                          .trim();

            // normalize punctuation to spaces
            out = out.replaceAll("[^A-Z0-9\\s]", " ");

            // remove common payment-processor / channel prefixes
            out = out.replaceFirst("^SQ\\s+", "");
            out = out.replaceFirst("^SP\\s+", "");
            out = out.replaceFirst("^PAYPAL\\s+", "");
            out = out.replaceFirst("^PP\\s+", "");

            // remove volatile tokens: dates, store nos, phone nos, long ids
            out = out.replaceAll("\\b\\d{1,2}/\\d{1,2}\\b", " ");
            out = out.replaceAll("\\b#\\s*\\d+\\b", " ");
            out = out.replaceAll("\\b\\d{3}[- ]\\d{3,}[- ]?\\d*\\b", " ");
            out = out.replaceAll("\\b\\d{4,}\\b", " ");

            // collapse spaces
            out = out.replaceAll("\\s+", " ").trim();
            return out;
        }
        
        private MerchantHit tryMatchAgainst(String text) {
            if (text == null || text.isEmpty()) return null;

            for (MerchantCompiled mc : compiled) {
                for (Pattern p : mc.patterns) {
                    if (p.matcher(text).find()) {
                        MerchantEntry e = mc.entry;
                        return new MerchantHit(
                                e.merchantId,
                                e.canonicalName,
                                e.defaultCategory,
                                e.defaultSub1,
                                e.defaultSub2,
                                e.defaultSub3,
                                e.defaultSub4,
                                e.defaults
                        );
                    }
                }
            }
            return null;
        }

        public String normalizeDesc(String s) {
            if (s == null) return "";
            
            s =    s.replace("Â", "")
            		.replace("\uFEFF", "")
                    .replace('\u00A0', ' ')
                    .replaceAll("[\\p{C}&&[^\r\n\t]]", " ")
                    .trim();
            String out = s;

            boolean doUpper = (map == null || map.normalization == null || map.normalization.uppercase == null || Boolean.TRUE.equals(map.normalization.uppercase));
            boolean stripPunct = map != null && map.normalization != null && Boolean.TRUE.equals(map.normalization.stripPunctuation);
            boolean collapseWs = map != null && map.normalization != null && Boolean.TRUE.equals(map.normalization.collapseWhitespace);

            if (doUpper) out = out.toUpperCase(Locale.ROOT);
            if (stripPunct) out = out.replaceAll("[^A-Z0-9\\s]", " ");
            // Always split/join using whitespace for ignore-token filtering; collapse if configured
            String[] parts = out.trim().split("\\s+");
            if (parts.length > 0) {
                StringBuilder sb = new StringBuilder(out.length());
                for (String tok : parts) {
                    if (tok == null || tok.isBlank()) continue;
                    String t = doUpper ? tok : tok.toUpperCase(Locale.ROOT);
                    if (ignoreTokens.contains(t)) continue;
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(doUpper ? tok : t);
                }
                out = sb.toString();
            }
            if (collapseWs) out = out.replaceAll("\\s+", " ").trim();
            return out;
        }
        
        private String rawForMerchantMatch(String s) {
            if (s == null) return "";
            // Same control-char cleanup you already do in normalizeDesc
            String out = s.replace("Â", "")
            			  .replace("\uFEFF", "")
                          .replace('\u00A0', ' ')
                          .replaceAll("[\\p{C}&&[^\r\n\t]]", " ")
                          .trim();

            // Uppercase so your patterns like "STORE" still match case-insensitively
            out = out.toUpperCase(Locale.ROOT);

            // IMPORTANT: do NOT remove punctuation or ignoreTokens here
            // (Optionally you can collapse whitespace)
            out = out.replaceAll("\\s+", " ").trim();
            return out;
        }
    }

    private static class MerchantCompiled {
        final MerchantEntry entry;
        final List<Pattern> patterns;
        MerchantCompiled(MerchantEntry entry, List<Pattern> patterns) {
            this.entry = entry;
            this.patterns = patterns;
        }
    }

    public static class MerchantHit {
        public final String merchantId;
        public final String canonicalName;

        // Legacy defaults (backward compatible)
        public final String defaultCategory;
        public final String defaultSub1;
        public final String defaultSub2;
        public final String defaultSub3;
        public final String defaultSub4;

        // Unified v3 defaults (preferred)
        public final DefaultsV3 defaults;

        public MerchantHit(
                String merchantId,
                String canonicalName,
                String defaultCategory,
                String defaultSub1,
                String defaultSub2,
                String defaultSub3,
                String defaultSub4,
                DefaultsV3 defaults
        ) {
            this.merchantId = merchantId;
            this.canonicalName = canonicalName;
            this.defaultCategory = defaultCategory;
            this.defaultSub1 = defaultSub1;
            this.defaultSub2 = defaultSub2;
            this.defaultSub3 = defaultSub3;
            this.defaultSub4 = defaultSub4;
            this.defaults = defaults;
        }
    }

    // ------------ JSON Models ------------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantMap {
        public Integer version;
        public String generatedAt;
        public Normalization normalization;
        public List<MerchantEntry> merchants;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Normalization {
        public Boolean uppercase;
        public Boolean collapseWhitespace;
        public Boolean stripPunctuation;
        // NEW: tokens to remove from description after normalization/tokenization
        public List<String> ignoreTokens;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantEntry {
        public String merchantId;
        public String canonicalName;

        // Legacy defaults (kept for backward compatibility)
        public String defaultCategory;
        public String defaultSub1;
        public String defaultSub2;
        public String defaultSub3; // your JSON already uses this in some entries
        public String defaultSub4;

        // Unified v3 defaults (preferred)
        public DefaultsV3 defaults;

        public MatchBlock match;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DefaultsV3 {
        public DefaultBlock base;
        public ByAmountSign byAmountSign;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ByAmountSign {
        public DefaultBlock negative;
        public DefaultBlock positive;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DefaultBlock {
        public String category;
        public String sub1;
        public String sub2;
        public String sub3;
        public String sub4;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchBlock {
        public List<String> patterns;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleSet {
        public Integer version;
        public String generatedAt;
        public EngineConfig engine;
        public List<Rule> rules;
    }

    // ------------ Check Rules (3rd JSON) ------------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckRuleSet {
        public Integer version;
        public String generatedAt;
        public List<CheckRule> checks;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CheckRule {
        public Boolean enabled;
        public String account;   // if blank/null, treated as wildcard "*"
        public Integer checkNo;
        public Actions then;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EngineConfig {
        public Boolean defaultStopOnMatch;
        public Boolean caseInsensitive;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rule {
        public Integer ruleNo;
        public String name;
        public Boolean enabled;
        public Integer priority;
        public Boolean stopOnMatch;
        public List<Condition> when;
        public Actions then;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        public String field; // description, account, amount, date, category, sub1..sub4, merchant
        public String op;    // EQ, CONTAINS, REGEX, GT, GTE, LT, LTE, IS_BLANK, IS_NOT_BLANK, BEFORE, AFTER, ON_OR_BEFORE, ON_OR_AFTER
        public Object value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Actions {
        public String setCategory;
        public String setSub1;
        public String setSub2;
        public String setSub3;
        public String setSub4;
    }

    // ------------ Helpers ------------
    private static String upper(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim()); }
    private static Object norm(Object o) { return (o instanceof String) ? ((String) o).trim() : o; }

    private static double toDouble(Object o) {
        if (o == null) return 0.0;
        if (o instanceof Number) {
            Number n = (Number) o;
            return n.doubleValue();
        }
        //if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(o).trim()); }
        catch (Exception e) { return 0.0; }
    }

    private static LocalDate parseDate(Object v) {
        if (v == null) return null;
        try { return LocalDate.parse(String.valueOf(v).trim()); }
        catch (Exception e) { return null; }
    }
    
    public static void main(String[] args) throws Exception {
    	// test
    	String[] desc = {
    			"B&N MEMBERSHIP RENEWAL 866-238-7323 NY",
    			"WITHDRAWAL 12/28",
    			"Annamraju Pmt from 1042965076 CK",
    			"RIA FINANCIAL SE DES:CORP PMT ID:XXXXX067738 INDN:KUMAR ANNAMRAJU CO ID:XXXXX29900 PPD",
    			"MATTHEW S. JOHNSON DDS MS",
    			"THE CHILDRENS PLACE #4255 WESLEY CHAPEL FL",
    			"Credit for Safe Deposit Box Eligible Refund",
    			"Credit for Safe Deposit Box Key Refund",
    			"Counter Credit",
    			"DescriptionDEPOSIT FROM ICICI BANKLIMI-EDI PAYMTSDEPOSIT FROM ICICI BANKLIMI-EDI PAYMTSMore infoSelect to Expand",
    			"PMT*VEHICLETAGREN HILL TAMPA FL",
    			"HC BOCC LIBRARY SERVICES",
    			"Â DaysInPeriod=31; Balance=0.00; Rate(APR)=8.490000; Rate(Daily)=0.023260",
    			"479829065 Annamraju",
    			"1SAFEDRIVER.COM 02/17 PURCHASE XXX-XX06400 FL",
    			"ANNAMRAJU KUMAR - 9590",
    			"IERNA'S HEATING &amp; COOLING",
    			"SQ *USA PRODUCE & ORGANIC Tampa FL",
    			"Â ITM-NEWTPA2",
    			"BKOFAMERICA BC 02/17 #XXXXX6280 WITHDRWL 7300 N MacArthur Irving TX",
    			"UBER * PENDI 09/01 PURCHASE San Francisco CA",
    			"LYFT 1 RIDE 12- 12/09 PURCHASE XXXXX59553 CA",
    			"ATH-RAP-RIVER-RUN 8886002298 CO",
    			"IRS  - USATAXPYMT",
    			"Â IRS - USATAXPYMT",
    			"IRS - USATAXPYMT",
    			"IRS USATAXPYMT",
    			"WITHDRAWAL 02/14",
    			"2025 PGDC REGISTRATION BOSTON MA",
    			"USA VOLLEYBALL",
    			"FL REGION OF USAV",
    			"FLORIDA ELITE VLYBALL",
    			"AAU",
    			"CLUB STEEL VBALL",
    			"MYERSTUTORING.ORG",
    			"TOPHAT.COM 01/24 PURCHASE DENVER CO",
    			"NOVASEUNIVBKSTORE 01/25 PURCHASE 954-262-4750 FL",
    			"NUMERADE 09/14 PURCHASE XXXXX63723 CA",
    			"Padmaja Dontaraju - ides",
    			"AWL*PEARSON EDUCATION",
    			"TOP HAT (COURSEWA 09/09 PURCHASE DENVER CO",
    			"WL *VUE*AAMC Exam",
    			"WL *VUE*AAMC EXAM 01/14 PURCHASE XXXXX13000 MN",
    			"E*TRADE BANK DES:TRANSFER ID:XXXXX2025601671 INDN:ETRADE BANK CO ID:XXXXX80001 CCD",
    			"Â OneSource Virtua EDI - EDI PYMNTS",
    			"THE EYE DOCTORS - TAMPA TAMPA FL",
    			"Zelle payment from PADMAJA JOSYULA USA0E5CB14F3",
    			"CHASE CREDIT CRD DES:RWRD RDM ID:CAZKGGGAIXNCMXQ INDN:DONTARAJU PADMAJA CO ID:XXXXX40001 PPD","Internet transfer from G T E FEDERAL CREDIT UNION DDA account ****************5076",
    			"Internet transfer to G T E FEDERAL CREDIT UNION DDA account ****************5076",
    			"Â OneSource Virtua EDI - EDI PYMNTS",
    			"AMZN DIGITAL 888-802-3080888-802- CREDIT",
    			"Payoff Excess Transfer from Loan 478699807",
    			"ADJUSTMENT TO BALANCE",
    			"ANNAMRAJU KUMAR - 912P",
    			"REMOTE ONLINE DEPOSIT #          1",
    			"UHI*U-HAULSTACK & STOR WESLEY CHAP FL",
    			"BANGKOK JAZZ TEMPLE TERRACFL",
    			"DAVINCI S A/S 10194777 TAMPA FL",
    			"Mobile/Email Transfer Conf# 19o39v9nw; venkatappa, muralidhara",
    			"SQ *ANGELA'S @ THE TAMPA FL",
    			"SQ *NIJEBA SHIKURI SeaTac WA",
    			"C1 - GLOBAL BAZAAR",
    			"LEXUS OF TAMPA BAY COL",
    			"236 WILLIAMS ST LOT &amp; GAR",
    			"RETURN OF POSTED CHECK / ITEM (RECEIVED ON 09-17) ELECTRONIC TRANSACTION",
    			"SQ *JEFFREY DANIEL",
    			"LagardÃ¨re Fil. 234 Frankfurt am DEU",
    			"PARCHMENT-UNIV DO 08/03 PURCHASE 480-719-1646 AZ",
    			"ME-WESLEY CHAPEL-MICRO LUTZ FL",
    			"EB GI JOES AMP AR 10/22 PURCHASE XXXXX37200 CA",
    			"HOFS HOUSE OF SWE 01/13 PURCHASE FORT LAUDERDA FL",
    			"Laderach USA Inc 01/26 PURCHASE Sunrise FL",
    			"SP JAPANESETASTE. 02/19 PURCHASE AMAGASAKISHI",
    			"SNACK SODA VENDIN 04/15 MOBILE PURCHASE HIALEAH FL",
    			"SNACK SODA VENDIN 04/22 MOBILE PURCHASE HIALEAH FL",
    			"TST* JENI'S SPLEN 10/05 PURCHASE TAMPA FL",
    			"Deposit from Kumar Annamraju",
    			"Outbound online wire to ************5186",
    			"DEPOSIT FROM GTEFIN CK WEBXFR-P2P YPE207455 SYSTEM GENERATED",
    			"WIRE TRANSFER TO Recipient: Annamraju, Kumar",
    			"110723CITIBANK, N.A.  DEPOSIT",
    			"DEPOSIT  ID NUMBER  65214",
    			"DEPOSIT  ID NUMBER  33893",
    			 "DEPOSIT  ID NUMBER 880248",
    			 "FEDWIRE CREDIT VIA: GOLDMAN SACHS BANK USA/124085260 B/O: GOLDMAN SACHS BANK USA NEW YORK NEW YORK UNITED STATES REF: CHASE NYC/CTR/BNF=KUMAR ANNAMRAJU OR PADMAJA TAMPA FL 33647-3737 US/AC -000000006822 RFB=O/B GOLDMAN SACH OBI=300335685017 ANNAMRAJU 30002518 8624 300335685017 ANNAMRAJU 3000251 88624 BBI= IMAD: 0724MMQFMP40000959 TRN: 1074531206FF",
    			 "E*TRADE BANK DES:TRANSFER ID:XXXXX2026217162 INDN:Annamraju,Kumar CO ID:XXXXX20001 PPD",
    			 "PAYMENT TO GTEFIN CK            WEBXFR-TRANSFER YPE117481 SYSTEM GENERATED YPE291247 SYSTEM GENERATED",
    			 "KUMAR ANNAMRAJU ONLNE TRNSFR88871081 - P2P",
    			 "GOLDMAN SACHS BANK USA-300025188624 ACH Orig",
    			 "WWW.HASHCHING.COM.AU",
    			 "WITHDRAWAL 12/28",
    			 "WIRE TYPE:WIRE IN DATE: 250210 TIME:0850 ET TRN:XXXXXXXXXX362711 SEQ:2JDXJKW55QF/000739 ORIG:KUMAR ANNAMRAJU ID:XXXXX5188624 SND BK:GOLDMA N SACHS BANK USA ID:XXXXX5260 PMT DET:MARCUS ONLIN E WIRE TRANSFER. 8624 D11AXXXXX18942D2900A451D091B",
    			 "KUMAR ANNAMRAJU ONLNE TRNSFR88871081 - P2P",
    			 "BKOFAMERICA ATM 03/27 #000001212 DEPOSIT CROSS CREEK TAMPA FL",
    			 "Deposit from Kumar Annamraju",
    			 "OPTUM BANK DES:REIMBUR ID:XXXXX7971 INDN:KUMAR ANNAMRAJU CO ID:XXXXX48773 PPD",
    			 "DIRECT DEPOSIT - GTE FINANCIAL,A2A.TRANFR",
    			 "RETURN OF POSTED CHECK / ITEM (RECEIVED ON 06-02) CHECK #0000000525",
    			 "BKOFAMERICA MOBILE 06/04 3561768778 DEPOSIT *MOBILE FL",
    			 "APPLE.COM/BILL 866-712-7753 CA",
    			 "APPLE.COM/US 800-676-2775 CA",
    			 "APPLE.COM/BILL 866-712-7753, CA",
    			 "APPLE.COM/BILL 1111111111, CA (3152)",
    			 "APPLE.COM/BILL 12/12 PURCHASE 866-712-7753 CA",
    			 "GPU ACH Initial Funding INTERNET 021313103 ***2234 Citizens Bank Nation",
    			 "GFM*GoFndMe* JJAMSSS O Redwood City CA",
    			 "DIRECT DEPOSIT - GTE FINANCIAL,A2A.TRANFR",
    			 "THE DEPOSITRY TR 134086405A - DIRECT DEP",
    			 "Â THE DEPOSITRY TR 134086405A - DIRECT DEP",
    			 "DescriptionDEPOSIT FROM THEDEPOSITORY T-DIRECT DEPDEPOSIT FROM THEDEPOSITORY T-DIRECT DEPMore infoSelect to Expand",
    			 "DEPOSIT FROM THEDEPOSITORY T-DIRECT DEPDEPOSIT FROM THEDEPOSITORY T-DIRECT DEPMore infoSelect to Expand",
    			 "DESCRIPTIONDEPOSIT THEDEPOSITORY T DIRECT DEPDEPOSIT THEDEPOSITORY T DIRECT DEPMORE INFOSELECT EXPAND",
    			 "DEPOSIT THEDEPOSITORY T DIRECT DEPDEPOSIT THEDEPOSITORY T DIRECT DEPMORE INFOSELECT EXPAND",
    			 "PAYMENT TO BANK OF AMERICA-TRIALDEBIT YPE170802 SYSTEM GENERATED",
    			 "DEPOSIT FROM BANK OF AMERICA-TRIALCREDT YPE167472 SYSTEM GENERATED",
    			 "ISPA/PIMDS 08/04 #000007829 WITHDRWL FCTI ISO JERSEY CITY NJ",
    			 "APPLE.COM/BILL 866-712-, CA",
    			 "MACYæ‹… EAST #841 WESLEY CHAPEL FL",
    			 "CVSPHARMACY #5149  Q03",
    			 "CVSPHARMACY #5251  Q03   TAMPA        FL",
    			 "CVSPHARMACY #5251  Q03   TAMPA        FL",
    			 "CVSPHARMACY #5251 Q03 TAMPA FL",
    			 "YANG MATH AND RE DES:PayrollDD ID:33984 INDN:Ananya Annamraju CO ID:XXXXX84145 PPD",
    			 "ATM Deposit GTE FCU 20761 CENTER OAK DRTAMPA FLUS",
    			 "ACH Electronic Debit - IITCITD5OIS5I37",
    			 "PAYMENT TO ICICI BANK  LIMI-EDI PAYMTS",
    			 "PAYMENT TO ICICI BANK  LIMI-EDI PAYMTS",
    			 "PAYMENT TO ICICI BANKLIMI-EDI PAYMTSPAYMENT TO ICICI BANKLIMI-EDI PAYMTS",
    			 "PAYMENT TO ICICI BANK  LIMI-EDI PAYMTS",
"VESTA  *AT&amp;T PREPAID",
"FL TLR cash withdrawal from CHK 5186 Banking Ctr CROSS CREEK #0001661 FL Confirmation# 4294210614",
"G&amp;M LAWN CARE AND M...",
"GTE FINANCIAL A2A.TRANFR",
"GTE FINANCIAL DES:A2A.TRANFR ID:T4841641:484057 INDN:Kumar Annamraju CO ID:Q590642956 PPD",
"CKE*BRANDON HONDA TAMP TAMPA FL",
"PAYPAL *MUSIC STORE 402-935-7733 CA",
"JO-ANN STORE #1861",
"T. KOMODA STORE AND BAKE",
"Zelle payment to Sharada Manginipudi for Padmaja's Music - Jan fee\"; Conf# mqd8j6otr\"",
"STORYBOOK TREATS         LAKE BUENA VIFL",
"THE EDISON 04/11 PURCHASE LAKE BUENA VI FL",
"Friscia & Ross, PA,Trust Account Bill Payment",
"NEW TAMPA ORAL &amp; FACIAL",
"DICK'S CLOTHING&amp;SPORTING WESLEY CHAPELFL",
"PAYMENT TO ICICI BANKLIMI-EDI PAYMTSPAYMENT TO ICICI BANKLIMI-EDI PAYMTS",
"New Tampa Foot &amp; Ankle"    			 ,
"253-SALE (DEBIT)-MYSAGEDENTAL.COM-TAMPA-FL-Oct 15 202"

    	};
        MerchantMap merchantMap;
        ObjectMapper om = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ClassLoader cl = LedgerRecordCategorizer.class.getClassLoader();
        try (InputStream mmIs = cl.getResourceAsStream("merchant_map.json")) {
            if (mmIs == null) {
                throw new FileNotFoundException("Resource not found: ");
            }
            merchantMap = om.readValue(mmIs, MerchantMap.class);
        }

    	MerchantNormalizer x =  new MerchantNormalizer(merchantMap);
    	for(int i = 0; i < desc.length; i++)
    		System.out.println(x.normalizeDesc(desc[i]));
    	
    }
}
