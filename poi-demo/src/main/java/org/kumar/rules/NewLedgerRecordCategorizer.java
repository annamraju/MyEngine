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

public class NewLedgerRecordCategorizer {
	private static final Logger LOGGER = LogManager.getLogger("LedgerCompleteness");

    // Macro tokens (can be used in merchant_map.json defaults OR rules.json actions)
    private static final String MACRO_ACCOUNT = "$ACCOUNT";
    private static final String MACRO_MERCHANT = "$MERCHANT";   // canonical merchant name
    private static final String MACRO_MERCHANT_ID = "$MERCHANT_ID"; // merchantId from merchant_map.json (optional)

    // ------------ Public API ------------
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


    /** Process one record and write category/sub1..sub4 back into the LedgerRecord. */
    public static CategorizationResult categorizeOne(Engine engine, LedgerRecord r) {
        return engine.process(r);
    }

    /** Process all. */
    public static List<CategorizationResult> categorizeAll(Engine engine, List<LedgerRecord> ledger) {
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
        public final boolean ruleMatched;
        public final Integer ruleNoMatched;
        public CategoryResult categoryResult;
        
        public CategorizationResult(LedgerRecord record, String merchant, boolean ruleMatched, Integer ruleNoMatched, CategoryResult res) {
            this.record = record;
            this.merchant = merchant;
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

        public Engine(MerchantNormalizer normalizer, RuleSet ruleSet, ZoneId zoneId) {
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
        }

        public CategorizationResult process(LedgerRecord r) {
            // Merchant from description
            MerchantHit mh = normalizer.infer(r.getDescription());
            String merchant = (mh != null) ? mh.canonicalName : null;

            boolean matchedAny = false;
            Integer matchedRuleNo = null;

            CategoryResult res =  null;
            for (Rule rule : rulesOrdered) {
                if (matchesAll(r, merchant, rule.when)) {
                    matchedAny = true;
                    matchedRuleNo = rule.ruleNo;

                    res =  applyActions(r, merchant, mh, rule.then);

                    boolean stop = (rule.stopOnMatch != null) ? rule.stopOnMatch : defaultStopOnMatch;
                    if (stop) break;
                }
            }

            // Apply merchant defaults only if category/sub1 are blank
            if (mh != null) {
                if ((res == null || isBlank(res.r_category)) && !isBlank(mh.defaultCategory)) {
                    if (res == null) res = new CategoryResult();
                    res.r_category = resolveMacro(mh.defaultCategory, r, merchant, mh);
                }
                if ((res == null || isBlank(res.r_sub1)) && !isBlank(mh.defaultSub1)) {
                    if (res == null) res = new CategoryResult();
                    res.r_sub1 = resolveMacro(mh.defaultSub1, r, merchant, mh);
                }
                if ((res == null || isBlank(res.r_sub2)) && !isBlank(mh.defaultSub2)) {
                    if (res == null) res = new CategoryResult();
                    res.r_sub2 = resolveMacro(mh.defaultSub2, r, merchant, mh);
                }
                // NEW: defaultSub3 support (including $ACCOUNT macro)
                if ((res == null || isBlank(res.r_sub3)) && !isBlank(mh.defaultSub3)) {
                    if (res == null) res = new CategoryResult();
                    res.r_sub3 = resolveMacro(mh.defaultSub3, r, merchant, mh);
                }
            }

            r.setMerchantId(merchant);
            r.setRuleNo(matchedRuleNo);
            return new CategorizationResult(r, merchant, matchedAny, matchedRuleNo, res);
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
            		fieldValue =  r.getDescription(); 
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
            if (!isBlank(a.setCategory)) res.r_category = resolveMacro(a.setCategory, r, merchant, mh);
            if (!isBlank(a.setSub1)) res.r_sub1 = resolveMacro(a.setSub1, r, merchant, mh);
            if (!isBlank(a.setSub2)) res.r_sub2 = resolveMacro(a.setSub2, r, merchant, mh);
            if (!isBlank(a.setSub3)) res.r_sub3 = resolveMacro(a.setSub3, r, merchant, mh);
            if (!isBlank(a.setSub4)) res.r_sub4 = resolveMacro(a.setSub4, r, merchant, mh);
            return res;
        }
        
        private String resolveMacro(String raw, LedgerRecord r, String merchant, MerchantHit mh) {
            if (raw == null) return null;

            // Support macros anywhere in the string (case-insensitive):
            //   $ACCOUNT      -> LedgerRecord.account
            //   $MERCHANT     -> matched merchant canonicalName
            //   $MERCHANT_ID  -> merchantId from merchant_map.json (if you decide to expose it later)
            String out = raw;

            String account = str(r.getAccount()).trim();
            String merch = str(merchant).trim();
            String merchId = (mh != null) ? str(mh.merchantId).trim() : "";

            out = out.replaceAll("(?i)\\Q" + MACRO_ACCOUNT + "\\E", java.util.regex.Matcher.quoteReplacement(account));
            out = out.replaceAll("(?i)\\Q" + MACRO_MERCHANT + "\\E", java.util.regex.Matcher.quoteReplacement(merch));
            out = out.replaceAll("(?i)\\Q" + MACRO_MERCHANT_ID + "\\E", java.util.regex.Matcher.quoteReplacement(merchId));

            out = out.trim();
            return out.isEmpty() ? null : out;
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
        private final List<AnchorCompiled> anchorCompiled;
        private final Set<String> modifierTokens;

        // Reasonable defaults (you can override/extend via merchant_map.json normalization.ignoreTokens)
        private static final Set<String> DEFAULT_IGNORE_TOKENS = new HashSet<>(Arrays.asList(
                // Location / common noise
                "FL", "TAMPA", "WESLEY", "CHAPEL", "CHAPELFL", "NY", "NJ", "PA",
                // Generic phrasing noise
                "THE", "PURCHASE", "FROM", "ID", "CONFIRMATION", "COM", "IA",
                "ONLINE", "TO", "SAV", "SCHEDULED", "DEP", "BILL", "PAYMENT",
                // Other common connective words (keep if you prefer; can be overridden by JSON)
                "OF"
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

            // Build modifier tokens (uppercased). You can extend this list in code as you discover new patterns.
            Set<String> mods = new HashSet<>(Arrays.asList(
                    "PAYPAL", "SQ", "SQUARE", "VENMO", "CASHAPP", "CASH", "APPLE", "GOOGLE"
            ));
            // Optional: allow JSON to provide modifierTokens (under normalization.modifierTokens)
            if (map != null && map.normalization != null && map.normalization.modifierTokens != null) {
                for (String w : map.normalization.modifierTokens) {
                    if (w == null || w.isBlank()) continue;
                    mods.add(w.trim().toUpperCase(Locale.ROOT));
                }
            }
            this.modifierTokens = Collections.unmodifiableSet(mods);

            // Compile anchors (if present)
            List<AnchorCompiled> acList = new ArrayList<>();
            if (map != null && map.anchors != null) {
                for (Anchor a : map.anchors) {
                    if (a == null) continue;
                    List<Pattern> pats = new ArrayList<>();
                    if (a.patterns != null) {
                        for (String raw : a.patterns) {
                            if (raw == null || raw.isBlank()) continue;
                            pats.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
                        }
                    }
                    List<String> toks = new ArrayList<>();
                    if (a.tokens != null) {
                        for (String t : a.tokens) {
                            if (t == null || t.isBlank()) continue;
                            toks.add(t.trim().toUpperCase(Locale.ROOT));
                        }
                    }
                    acList.add(new AnchorCompiled(a, pats, toks));
                }
            }
            this.anchorCompiled = Collections.unmodifiableList(acList);

        }

        
        /**
         * Infer merchant for a description using the following pipeline:
         *   1) Special-case routing: descriptions starting with "Zelle" (case-sensitive) or "Check" (case-insensitive)
         *      are expected to be handled by dedicated rules. We still return a synthetic merchant so rules can match on MERCHANT.
         *   2) merchant_map.json regex patterns (highest precision)
         *   3) Anchors (if configured in merchant_map.json) - intended to uniquely identify a merchant
         *   4) Token/bigram inference (dominant token/bigram and simple modifier handling)
         */
        public MerchantHit infer(String description) {
            if (description == null) description = "";

            // 1) Special-case: Zelle / Check handled by dedicated rules
            if (description.startsWith("Zelle")) {
                return new MerchantHit("ZELLE", "ZELLE", null, null, null, null);
            }
            if (description.regionMatches(true, 0, "Check", 0, "Check".length())) {
                return new MerchantHit("CHECK", "CHECK", null, null, null, null);
            }

            // 2) Merchant map regex (precision)
            MerchantHit mm = matchMerchantMap(description);
            if (mm != null) return mm;

            // Normalize for anchor + token inference
            String normalized = normalizeDescForInference(description);

            // 3) Anchors (configured)
            if (!anchorCompiled.isEmpty()) {
                for (AnchorCompiled ac : anchorCompiled) {
                    if (ac == null) continue;
                    // Regex patterns first
                    for (Pattern p : ac.patterns) {
                        if (p.matcher(normalized).find()) {
                            Anchor a = ac.anchor;
                            return new MerchantHit(a.merchantId, a.canonicalName, a.defaultCategory, a.defaultSub1, a.defaultSub2, a.defaultSub3);
                        }
                    }
                    // Token/bigram anchors (contains)
                    if (ac.tokensUpper != null && !ac.tokensUpper.isEmpty()) {
                        for (String t : ac.tokensUpper) {
                            if (t == null || t.isBlank()) continue;
                            if (containsTokenOrBigram(normalized, t)) {
                                Anchor a = ac.anchor;
                                return new MerchantHit(a.merchantId, a.canonicalName, a.defaultCategory, a.defaultSub1, a.defaultSub2, a.defaultSub3);
                            }
                        }
                    }
                }
            }

            // 4) Token/bigram dominant inference (fallback)
            String inferred = inferMerchantFromTokens(normalized);
            if (isBlank(inferred)) return null;

            // merchantId is optional for inferred merchants; set to canonical for convenience
            return new MerchantHit(inferred, inferred, null, null, null, null);
        }

        /** Backward compatible name - this is the "merchant_map regex" match only. */
        public MerchantHit match(String description) {
            return matchMerchantMap(description);
        }

        private MerchantHit matchMerchantMap(String description) {
            String normalized = normalizeDesc(description);
            for (MerchantCompiled mc : compiled) {
                for (Pattern p : mc.patterns) {
                    if (p.matcher(normalized).find()) {
                        MerchantEntry e = mc.entry;
                        return new MerchantHit(e.merchantId, e.canonicalName, e.defaultCategory, e.defaultSub1, e.defaultSub2, e.defaultSub3);
                    }
                }
            }
            return null;
        }


        private String normalizeDesc(String s) {
            if (s == null) return "";
            s =    s.replace("\uFEFF", "")
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

        // ---------------- Inference helpers ----------------

        /**
         * Normalization used for anchor/token inference:
         * - uppercases
         * - keeps &, -, ' so merchants like AT&T, WAL-MART, LIANG'S survive
         * - removes most other punctuation
         * - removes ignoreTokens (configured)
         * - collapses whitespace
         */
        private String normalizeDescForInference(String s) {
            if (s == null) return "";
            String out = s;

            boolean doUpper = (map == null || map.normalization == null || map.normalization.uppercase == null || Boolean.TRUE.equals(map.normalization.uppercase));
            if (doUpper) out = out.toUpperCase(Locale.ROOT);

            // Keep &, -, ' for merchant inference; replace other punctuation with space
            out = out.replaceAll("[^A-Z0-9\\s&'\\-]", " ");

            // Remove ignore tokens
            String[] parts = out.trim().split("\\s+");
            if (parts.length > 0) {
                StringBuilder sb = new StringBuilder(out.length());
                for (String tok : parts) {
                    if (tok == null || tok.isBlank()) continue;
                    String t = tok.toUpperCase(Locale.ROOT);
                    // ignore numerals and single-char tokens
                    if (t.matches("^\\d+$")) continue;
                    if (t.length() <= 1) continue;
                    if (ignoreTokens.contains(t)) continue;
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t);
                }
                out = sb.toString();
            }

            out = out.replaceAll("\\s+", " ").trim();
            return out;
        }

        private boolean containsTokenOrBigram(String normalized, String tokenOrBigramUpper) {
            if (isBlank(normalized) || isBlank(tokenOrBigramUpper)) return false;
            String needle = tokenOrBigramUpper.trim().toUpperCase(Locale.ROOT);
            // whole-word-ish match (space bounded) to reduce false positives
            String hay = " " + normalized.toUpperCase(Locale.ROOT) + " ";
            return hay.contains(" " + needle + " ");
        }

        /**
         * Dominant token/bigram inference:
         * - build tokens (A-Z0-9 plus &, -, ')
         * - ignore numerals, single-char, ignoreTokens
         * - prefer bigram if it looks more "merchant-like" than any single token
         * - basic modifier handling: if a modifier token is present, prefer the token/bigram immediately after it
         */
        private String inferMerchantFromTokens(String normalized) {
            if (isBlank(normalized)) return null;

            List<String> tokens = tokenizeForInference(normalized);
            if (tokens.isEmpty()) return null;

            // Modifier handling: if we see a modifier token, prefer what follows (e.g., "PAYPAL *FOO", "SQ *BAR")
            for (int i = 0; i < tokens.size() - 1; i++) {
                String t = tokens.get(i);
                if (modifierTokens.contains(t)) {
                    String next = tokens.get(i + 1);
                    if (!isBlank(next)) return next;
                }
            }

            // Score tokens and bigrams
            String bestToken = null;
            int bestTokenScore = -1;
            for (String t : tokens) {
                int score = scoreToken(t);
                if (score > bestTokenScore) {
                    bestTokenScore = score;
                    bestToken = t;
                }
            }

            String bestBigram = null;
            int bestBigramScore = -1;
            for (int i = 0; i < tokens.size() - 1; i++) {
                String bg = tokens.get(i) + " " + tokens.get(i + 1);
                int score = scoreBigram(bg);
                if (score > bestBigramScore) {
                    bestBigramScore = score;
                    bestBigram = bg;
                }
            }

            // Prefer bigram if it is clearly stronger
            if (!isBlank(bestBigram) && bestBigramScore >= bestTokenScore + 3) {
                return bestBigram;
            }
            return bestToken;
        }

        private List<String> tokenizeForInference(String normalized) {
            if (isBlank(normalized)) return Collections.emptyList();
            // tokens start with a letter; keep letters/digits and &, -, '
            Pattern tokenPat = Pattern.compile("[A-Z][A-Z0-9&'\\-]*");
            java.util.regex.Matcher m = tokenPat.matcher(normalized.toUpperCase(Locale.ROOT));
            List<String> out = new ArrayList<>();
            while (m.find()) {
                String t = m.group();
                if (t == null) continue;
                t = t.trim().toUpperCase(Locale.ROOT);
                if (t.isEmpty()) continue;
                if (t.matches("^\\d+$")) continue;
                if (t.length() <= 1) continue;
                if (ignoreTokens.contains(t)) continue;
                out.add(t);
            }
            return out;
        }

        private int scoreToken(String t) {
            if (t == null) return -1;
            int score = 0;
            String tok = t.trim();
            score += Math.min(tok.length(), 20); // length helps merchant-ness
            if (tok.contains("&")) score += 4;
            if (tok.contains("-")) score += 3;
            if (tok.contains("'")) score += 2;
            // Penalize very generic-looking tokens (heuristic)
            if (tok.equals("TRANSFER") || tok.equals("WITHDRAWAL") || tok.equals("DEPOSIT")) score -= 10;
            return score;
        }

        private int scoreBigram(String bg) {
            if (bg == null) return -1;
            int score = 0;
            score += Math.min(bg.length(), 30);
            // Slight bonus: multi-word merchants are often HOA etc.
            score += 2;
            return score;
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

    private static class AnchorCompiled {
        final Anchor anchor;
        final List<Pattern> patterns;
        final List<String> tokensUpper;

        AnchorCompiled(Anchor anchor, List<Pattern> patterns, List<String> tokensUpper) {
            this.anchor = anchor;
            this.patterns = patterns == null ? Collections.emptyList() : patterns;
            this.tokensUpper = tokensUpper == null ? Collections.emptyList() : tokensUpper;
        }
    }


    public static class MerchantHit {
        public final String merchantId;
        public final String canonicalName;
        public final String defaultCategory;
        public final String defaultSub1;
        public final String defaultSub2;
        public final String defaultSub3;
        
        public MerchantHit(String merchantId, String canonicalName, String defaultCategory, String defaultSub1, String defaultSub2, String defaultSub3) {
            this.merchantId = merchantId;
            this.canonicalName = canonicalName;
            this.defaultCategory = defaultCategory;
            this.defaultSub1 = defaultSub1;
            this.defaultSub2 = defaultSub2;
            this.defaultSub3 = defaultSub3;
        }
    }

    // ------------ JSON Models ------------
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantMap {
        public Integer version;
        public String generatedAt;
        public Normalization normalization;
        public List<MerchantEntry> merchants;
        // Optional: Anchors are high-confidence identifiers that uniquely map to a merchant.
        public List<Anchor> anchors;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Normalization {
        public Boolean uppercase;
        public Boolean collapseWhitespace;
        public Boolean stripPunctuation;
        // NEW: tokens to remove from description after normalization/tokenization
        public List<String> ignoreTokens;
        // Optional: tokens that act as 'modifiers' (e.g., PAYPAL, SQ) where the following token is the merchant.
        public List<String> modifierTokens;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantEntry {
        public String merchantId;
        public String canonicalName;
        public String defaultCategory;
        public String defaultSub1;
        public String defaultSub2;
        public String defaultSub3; // NEW: your JSON already uses this in some entries        
        public MatchBlock match;
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Anchor {
        public String merchantId;
        public String canonicalName;

        // Optional defaults (same semantics as MerchantEntry defaults)
        public String defaultCategory;
        public String defaultSub1;
        public String defaultSub2;
        public String defaultSub3;

        // Either patterns (regex) or tokens (exact token/bigram contains) can be used
        public List<String> patterns;
        public List<String> tokens;
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
}
