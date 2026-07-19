package org.kumar.dataload.util;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.kumar.excel.util.LedgerRecord;

/**
 * Reader for E*TRADE brokerage PDF statements.
 *
 * Supports two statement layouts:
 * <ul>
 *   <li>Legacy: TRANSACTION HISTORY with Securities / Dividends / Withdrawals subsections</li>
 *   <li>Newer Morgan Stanley style: ACTIVITY / CASH FLOW ACTIVITY BY DATE</li>
 * </ul>
 *
 * Loaded activity:
 * - Securities purchased or sold
 * - Unsettled trades
 * - Dividends and interest activity
 * - Withdrawals and deposits (Transfer / Online Transfer only)
 * - Other activity and MMF/BDP automatic investments are skipped
 */
public class EtradeBrokerageRecord implements LedgerInterface {

    private static final DateTimeFormatter DATE_FMT =
            new DateTimeFormatterBuilder()
                    .appendPattern("MM/dd/")
                    .appendValueReduced(ChronoField.YEAR, 2, 4, 2000)
                    .toFormatter();

    private static final Pattern TRADE_DATE = Pattern.compile("^\\d{2}/\\d{2}/\\d{2}$");
    private static final Pattern TRADE_DATE_PREFIX = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{2})(?:\\s+\\d{2}/\\d{2}/\\d{2})?\\s*(.*)$");
    private static final Pattern TRADE_TIME = Pattern.compile("^\\d{2}:\\d{2}$");
    private static final Pattern SECURITIES_TAIL = Pattern.compile(
            "(?:^|.*\\s)(Bought|Sold)\\s+(-?\\d+(?:\\.\\d+)?)\\s+([\\d,]+\\.\\d+)\\s+([\\d,]+\\.\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIVIDEND_START = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{2})\\s+(Dividend|Interest)\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CASH_START = Pattern.compile(
            "^(\\d{2}/\\d{2}/\\d{2})\\s+(Adjustment|Credit|Transfer|Mark to Mkt)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SYMBOL_AMOUNT = Pattern.compile(
            "^([A-Z*][A-Z0-9*.]{0,12})\\s+([\\d,]+\\.\\d{2})(?:\\s+([\\d,]+\\.\\d{2}))?$");
    private static final Pattern AMOUNT_ONLY = Pattern.compile("^\\$?([\\d,]+\\.\\d{2})$");
    private static final Pattern TRAILING_AMOUNT = Pattern.compile("\\s+\\$?([\\d,]+\\.\\d{2})\\s*$");

    /** Newer statements use M/D (no year) plus optional settlement date. */
    private static final Pattern ACTIVITY_DATE_PREFIX = Pattern.compile(
            "^(\\d{1,2})/(\\d{1,2})(?:\\s+(\\d{1,2})/(\\d{1,2}))?\\s+(.+)$");
    private static final Pattern ACTIVITY_TYPE_PREFIX = Pattern.compile(
            "^(Qualified Dividend|Dividend|Interest Income|Interest|Online Transfer|Transfer|Bought|Sold)\\b\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTIVITY_QTY_PRICE_AMT = Pattern.compile(
            "^\\$?([\\d,]+\\.\\d+)\\s+\\$?([\\d,]+\\.\\d+)\\s+\\$?([\\d,]+\\.\\d{2})$");
    private static final Pattern STATEMENT_PERIOD_YEAR = Pattern.compile(
            "For the Period\\s+.+?(\\d{4})\\s*$",
            Pattern.CASE_INSENSITIVE);

    private LocalDate transactionDate;
    private String section;
    private String transactionType;
    private String symbol;
    private String description;
    private double amount;

    public EtradeBrokerageRecord() {}

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    @Override
    public String toString() {
        return "EtradeBrokerageRecord{" +
                "transactionDate=" + transactionDate +
                ", section='" + section + '\'' +
                ", transactionType='" + transactionType + '\'' +
                ", symbol='" + symbol + '\'' +
                ", description='" + description + '\'' +
                ", amount=" + amount +
                '}';
    }

    @Override
    public LedgerRecord convertToLedgerRecord(String accountName, short order) {
        short year = (short) transactionDate.getYear();
        String month = transactionDate.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        Date transDate = Date.from(transactionDate.atStartOfDay(ZoneId.systemDefault()).toInstant());

        return new LedgerRecord(
                accountName,
                order,
                year,
                month,
                transDate,
                description,
                amount,
                0.0,
                null, null, null, null, null
        );
    }

    public static List<EtradeBrokerageRecord> readTransactionsFromPdf(Path pdfPath) throws IOException {
        return parseTransactionsFromText(extractText(pdfPath));
    }

    static List<EtradeBrokerageRecord> parseTransactionsFromText(String text) throws IOException {
        List<String> lines = normalizeLines(text);

        int legacyStart = indexOfContains(lines, "TRANSACTION HISTORY");
        int activityStart = indexOfExactIgnoreCase(lines, "ACTIVITY");

        List<EtradeBrokerageRecord> out;
        if (legacyStart >= 0) {
            out = parseLegacyTransactionHistory(lines, legacyStart + 1);
        } else if (activityStart >= 0) {
            out = parseModernActivity(lines, activityStart + 1);
        } else {
            throw new IOException(
                    "Could not find 'TRANSACTION HISTORY' or 'ACTIVITY' section in PDF text");
        }

        out.sort(Comparator
                .comparing(EtradeBrokerageRecord::getTransactionDate)
                .thenComparing(EtradeBrokerageRecord::getSection, Comparator.nullsLast(String::compareTo))
                .thenComparing(EtradeBrokerageRecord::getDescription, Comparator.nullsLast(String::compareTo)));

        return out;
    }

    private static List<EtradeBrokerageRecord> parseLegacyTransactionHistory(List<String> lines, int start) {
        List<EtradeBrokerageRecord> out = new ArrayList<>();
        int i = start;
        while (i < lines.size()) {
            String line = lines.get(i);
            String upper = line.toUpperCase(Locale.ROOT);

            if (upper.startsWith("OTHER ACTIVITY")) {
                parseOtherActivity(lines, i + 1, out);
                break;
            }
            if (upper.startsWith("SECURITIES PURCHASED OR SOLD")
                    || upper.startsWith("UNSETTLED TRADES")) {
                i = parseSecuritiesSection(lines, i + 1, out, sectionName(line));
                continue;
            }
            if (upper.startsWith("DIVIDENDS & INTEREST ACTIVITY")) {
                i = parseDividendSection(lines, i + 1, out);
                continue;
            }
            if (upper.startsWith("WITHDRAWALS & DEPOSITS")) {
                i = parseCashSection(lines, i + 1, out);
                continue;
            }
            i++;
        }
        return out;
    }

    /**
     * Newer E*TRADE / Morgan Stanley statements use an ACTIVITY section with
     * CASH FLOW ACTIVITY BY DATE and UNSETTLED PURCHASES/SALES ACTIVITY.
     */
    private static List<EtradeBrokerageRecord> parseModernActivity(List<String> lines, int start) {
        int year = extractStatementYear(lines);
        List<EtradeBrokerageRecord> out = new ArrayList<>();
        java.util.Set<String> seenKeys = new java.util.HashSet<>();

        int i = start;
        while (i < lines.size()) {
            String upper = lines.get(i).toUpperCase(Locale.ROOT);
            if (upper.startsWith("CASH FLOW ACTIVITY BY DATE")) {
                i = parseModernCashFlowSection(lines, i + 1, out, seenKeys, year, "Securities");
                continue;
            }
            if (upper.startsWith("UNSETTLED PURCHASES/SALES ACTIVITY")) {
                i = parseModernCashFlowSection(lines, i + 1, out, seenKeys, year, "Unsettled Trades");
                continue;
            }
            if (upper.startsWith("MONEY MARKET FUND")
                    || upper.startsWith("NET ACTIVITY FOR PERIOD")
                    || upper.startsWith("MESSAGES")) {
                break;
            }
            i++;
        }
        return out;
    }

    private static int parseModernCashFlowSection(List<String> lines, int start, List<EtradeBrokerageRecord> out,
            java.util.Set<String> seenKeys, int year, String tradeSection) {
        LocalDate pendingDate = null;
        String pendingType = null;
        StringBuilder pendingDesc = new StringBuilder();

        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            String upper = line.toUpperCase(Locale.ROOT);

            if (isModernActivityBoundary(upper)) {
                flushModernPendingTrade(out, seenKeys, pendingDate, pendingType, pendingDesc, tradeSection);
                return i;
            }
            if (isModernActivityNoise(upper)) {
                continue;
            }

            Matcher dateMatcher = ACTIVITY_DATE_PREFIX.matcher(line);
            if (dateMatcher.matches()) {
                flushModernPendingTrade(out, seenKeys, pendingDate, pendingType, pendingDesc, tradeSection);
                pendingDate = null;
                pendingType = null;
                pendingDesc.setLength(0);

                LocalDate activityDate = LocalDate.of(
                        year,
                        Integer.parseInt(dateMatcher.group(1)),
                        Integer.parseInt(dateMatcher.group(2)));
                String rest = dateMatcher.group(5).trim();

                Matcher typeMatcher = ACTIVITY_TYPE_PREFIX.matcher(rest);
                if (!typeMatcher.matches()) {
                    continue;
                }

                String rawType = typeMatcher.group(1);
                String remainder = typeMatcher.group(2) == null ? "" : typeMatcher.group(2).trim();
                String normalizedType = normalizeModernActivityType(rawType);

                if (isSkippedModernActivityType(normalizedType, rawType)) {
                    continue;
                }

                if ("Bought".equalsIgnoreCase(normalizedType) || "Sold".equalsIgnoreCase(normalizedType)) {
                    Matcher inlineQty = findTrailingQtyPriceAmount(remainder);
                    if (inlineQty != null) {
                        String desc = remainder.substring(0, inlineQty.start()).trim();
                        double amount = parseMoney(inlineQty.group(3));
                        addModernTradeRecord(out, seenKeys, activityDate, normalizedType, desc, amount, tradeSection);
                    } else {
                        pendingDate = activityDate;
                        pendingType = normalizedType;
                        pendingDesc.setLength(0);
                        if (!remainder.isEmpty()) {
                            pendingDesc.append(remainder);
                        }
                    }
                    continue;
                }

                Matcher trailing = TRAILING_AMOUNT.matcher(remainder);
                if (!trailing.find()) {
                    continue;
                }
                String desc = remainder.substring(0, trailing.start()).trim();
                double amount = parseMoney(trailing.group(1));

                if ("Dividend".equalsIgnoreCase(normalizedType) || "Interest".equalsIgnoreCase(normalizedType)) {
                    // Interest Income is a credit; legacy margin Interest remains a debit via old parser.
                    double signed = amount;
                    if ("Interest".equalsIgnoreCase(normalizedType)
                            && !rawType.toUpperCase(Locale.ROOT).contains("INCOME")) {
                        signed = -Math.abs(amount);
                    } else {
                        signed = Math.abs(amount);
                    }
                    addDividendRecord(out, activityDate, normalizedType, desc, null, signed);
                    seenKeys.add(dedupeKey(activityDate, normalizedType, desc, signed));
                    continue;
                }

                if ("Transfer".equalsIgnoreCase(normalizedType)) {
                    boolean deposit = isModernTransferDeposit(desc);
                    addCashRecord(out, activityDate, "Transfer", desc, deposit, amount);
                    continue;
                }

                continue;
            }

            if (pendingDate != null) {
                Matcher qtyPriceAmt = ACTIVITY_QTY_PRICE_AMT.matcher(line);
                if (qtyPriceAmt.matches()) {
                    double amount = parseMoney(qtyPriceAmt.group(3));
                    addModernTradeRecord(out, seenKeys, pendingDate, pendingType, pendingDesc.toString(),
                            amount, tradeSection);
                    pendingDate = null;
                    pendingType = null;
                    pendingDesc.setLength(0);
                    continue;
                }
                appendDesc(pendingDesc, line.trim());
            }
        }

        flushModernPendingTrade(out, seenKeys, pendingDate, pendingType, pendingDesc, tradeSection);
        return lines.size();
    }

    private static void flushModernPendingTrade(List<EtradeBrokerageRecord> out, java.util.Set<String> seenKeys,
            LocalDate date, String type, StringBuilder desc, String tradeSection) {
        if (date == null || type == null || desc == null) {
            return;
        }
        String clean = desc.toString().trim();
        Matcher trailing = findTrailingQtyPriceAmount(clean);
        if (trailing == null) {
            return;
        }
        String descPart = clean.substring(0, trailing.start()).trim();
        double amount = parseMoney(trailing.group(3));
        addModernTradeRecord(out, seenKeys, date, type, descPart, amount, tradeSection);
    }

    private static void addModernTradeRecord(List<EtradeBrokerageRecord> out, java.util.Set<String> seenKeys,
            LocalDate date, String type, String desc, double amount, String section) {
        boolean bought = "Bought".equalsIgnoreCase(type);
        double signed = bought ? -Math.abs(amount) : Math.abs(amount);
        String cleanDesc = desc.replaceAll("\\s{2,}", " ").trim();
        if (cleanDesc.isEmpty()) {
            cleanDesc = type;
        }

        String key = dedupeKey(date, type, cleanDesc, signed);
        if (!seenKeys.add(key)) {
            return;
        }

        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(date);
        rec.setSection(section);
        rec.setTransactionType(type);
        rec.setDescription(cleanDesc);
        rec.setAmount(signed);
        out.add(rec);
    }

    private static Matcher findTrailingQtyPriceAmount(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile(
                "(?i)(?:UNSETTLED\\s+(?:SALE|PURCHASE)\\s+)?\\$?([\\d,]+\\.\\d+)\\s+\\$?([\\d,]+\\.\\d+)\\s+\\$?([\\d,]+\\.\\d{2})\\s*$")
                .matcher(text.trim());
        return m.find() ? m : null;
    }

    private static String normalizeModernActivityType(String rawType) {
        String upper = rawType.toUpperCase(Locale.ROOT);
        if (upper.contains("DIVIDEND")) {
            return "Dividend";
        }
        if (upper.contains("INTEREST")) {
            return "Interest";
        }
        if (upper.contains("TRANSFER")) {
            return "Transfer";
        }
        if (upper.equals("BOUGHT")) {
            return "Bought";
        }
        if (upper.equals("SOLD")) {
            return "Sold";
        }
        return rawType;
    }

    private static boolean isSkippedModernActivityType(String normalizedType, String rawType) {
        String upper = rawType.toUpperCase(Locale.ROOT);
        return upper.contains("AUTOMATIC INVESTMENT")
                || upper.startsWith("AUTOMATIC");
    }

    private static boolean isModernTransferDeposit(String description) {
        if (description == null) {
            return false;
        }
        String upper = description.toUpperCase(Locale.ROOT);
        if (upper.contains("WITHDRAW") || upper.contains("TRANSFER TO") || upper.contains("ACH WITHDRAWAL")) {
            return false;
        }
        return upper.contains("DEPOSIT") || upper.contains("TRANSFER FROM") || upper.contains("ACH DEPOSIT");
    }

    private static boolean isModernActivityBoundary(String upper) {
        return upper.startsWith("UNSETTLED PURCHASES/SALES ACTIVITY")
                || upper.startsWith("MONEY MARKET FUND")
                || upper.startsWith("NET CREDITS/(DEBITS)")
                || upper.startsWith("NET UNSETTLED PURCHASES/SALES")
                || upper.startsWith("NET ACTIVITY FOR PERIOD")
                || upper.startsWith("MESSAGES");
    }

    private static boolean isModernActivityNoise(String upper) {
        return upper.equals("ACTIVITY")
                || upper.equals("DATE")
                || upper.startsWith("SETTLEMENT")
                || upper.startsWith("ACTIVITY TYPE")
                || upper.startsWith("DESCRIPTION")
                || upper.startsWith("COMMENTS")
                || upper.startsWith("QUANTITY")
                || upper.equals("PRICE")
                || upper.startsWith("CREDITS/(DEBITS)")
                || upper.startsWith("PENDING")
                || upper.startsWith("PAGE ")
                || upper.startsWith("ACCOUNT DETAIL")
                || upper.startsWith("CLIENT STATEMENT")
                || upper.startsWith("SELF-DIRECTED")
                || upper.startsWith("SUBJECT TO STA")
                || upper.startsWith("PURCHASE AND SALE TRANSACTIONS")
                || upper.startsWith("THIS SECTION DISPLAYS")
                || upper.startsWith("THE HOLDINGS SECTION")
                || upper.startsWith("PRICE FOR UNSETTLED")
                || Pattern.compile("^\\d{3}-\\d{6}-\\d{3}$").matcher(upper).matches()
                || upper.matches("[A-Z ]+ TOD");
    }

    private static int extractStatementYear(List<String> lines) {
        for (String line : lines) {
            Matcher m = STATEMENT_PERIOD_YEAR.matcher(line.trim());
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        }
        // Fallback: current year is better than failing hard on missing header text.
        return LocalDate.now().getYear();
    }

    private static String dedupeKey(LocalDate date, String type, String desc, double amount) {
        String normalizedDesc = desc == null ? "" : desc.toUpperCase(Locale.ROOT)
                .replaceAll("\\bUNSETTLED\\s+(?:SALE|PURCHASE)\\b", " ")
                .replaceAll("\\bACTED AS AGENT\\b", " ")
                .replaceAll("\\bUNSOLICITED TRADE;?\\b", " ")
                .replaceAll("\\bOPENING\\b", " ")
                .replaceAll("\\bCLOSING\\b", " ")
                .replaceAll("[^A-Z0-9./]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return date + "|" + String.valueOf(type).toUpperCase(Locale.ROOT) + "|"
                + normalizedDesc + "|" + String.format(Locale.ROOT, "%.2f", amount);
    }

    private static int indexOfExactIgnoreCase(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equalsIgnoreCase(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static int parseSecuritiesSection(List<String> lines, int start, List<EtradeBrokerageRecord> out,
            String section) {
        List<String> buffer = new ArrayList<>();

        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isSectionBoundary(line)) {
                return i;
            }
            if (isSecuritiesNoise(line)) {
                continue;
            }

            Matcher tail = SECURITIES_TAIL.matcher(line);
            if (tail.matches()) {
                EtradeBrokerageRecord rec = buildSecuritiesRecord(buffer, line, tail, section);
                if (rec != null) {
                    out.add(rec);
                }
                buffer.clear();
                continue;
            }

            buffer.add(line);
        }
        return lines.size();
    }

    private static int parseDividendSection(List<String> lines, int start, List<EtradeBrokerageRecord> out) {
        LocalDate curDate = null;
        String curType = null;
        StringBuilder curDesc = new StringBuilder();

        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isSectionBoundary(line)) {
                return i;
            }
            if (isDividendNoise(line)) {
                continue;
            }

            Matcher startMatcher = DIVIDEND_START.matcher(line);
            if (startMatcher.find()) {
                flushDividendIfReady(out, curDate, curType, curDesc.toString());
                curDate = parseDate(startMatcher.group(1));
                curType = startMatcher.group(2);
                curDesc.setLength(0);
                String rest = startMatcher.group(3).trim();
                if (!rest.isEmpty()) {
                    curDesc.append(rest);
                }
                continue;
            }

            if (curDate != null) {
                Matcher symbolAmount = SYMBOL_AMOUNT.matcher(line);
                if (symbolAmount.find()) {
                    String symbol = symbolAmount.group(1);
                    double debited = parseMoney(symbolAmount.group(2));
                    String creditedStr = symbolAmount.group(3);
                    double signed = creditedStr == null
                            ? debited
                            : parseMoney(creditedStr) - debited;
                    addDividendRecord(out, curDate, curType, curDesc.toString(), symbol, signed);
                    curDate = null;
                    curType = null;
                    curDesc.setLength(0);
                    continue;
                }

                Matcher amountOnly = AMOUNT_ONLY.matcher(line);
                if (amountOnly.find()) {
                    double signed = "Interest".equalsIgnoreCase(curType)
                            ? -parseMoney(amountOnly.group(1))
                            : parseMoney(amountOnly.group(1));
                    addDividendRecord(out, curDate, curType, curDesc.toString(), null, signed);
                    curDate = null;
                    curType = null;
                    curDesc.setLength(0);
                    continue;
                }

                curDesc.append(" ").append(line.trim());
            }
        }
        flushDividendIfReady(out, curDate, curType, curDesc.toString());
        return lines.size();
    }

    private static int parseCashSection(List<String> lines, int start, List<EtradeBrokerageRecord> out) {
        LocalDate curDate = null;
        String curType = null;
        StringBuilder curDesc = new StringBuilder();
        boolean depositColumn = false;

        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isSectionBoundary(line)) {
                return i;
            }
            if (isCashNoise(line)) {
                continue;
            }

            Matcher startMatcher = CASH_START.matcher(line);
            if (startMatcher.find()) {
                flushCashIfReady(out, curDate, curType, curDesc.toString(), depositColumn);
                curDate = parseDate(startMatcher.group(1));
                curType = startMatcher.group(2);
                curDesc.setLength(0);
                String rest = startMatcher.group(3).trim();
                depositColumn = "Credit".equalsIgnoreCase(curType);

                Matcher trailing = TRAILING_AMOUNT.matcher(rest);
                if (trailing.find()) {
                    String descPart = rest.substring(0, trailing.start()).trim();
                    if (!descPart.isEmpty()) {
                        curDesc.append(descPart);
                    }
                    double amount = parseMoney(trailing.group(1));
                    addCashRecord(out, curDate, curType, curDesc.toString(), depositColumn, amount);
                    curDate = null;
                    curType = null;
                    curDesc.setLength(0);
                } else if (!rest.isEmpty()) {
                    curDesc.append(rest);
                }
                continue;
            }

            if (curDate != null) {
                Matcher amountOnly = AMOUNT_ONLY.matcher(line);
                if (amountOnly.find()) {
                    addCashRecord(out, curDate, curType, curDesc.toString(), depositColumn,
                            parseMoney(amountOnly.group(1)));
                    curDate = null;
                    curType = null;
                    curDesc.setLength(0);
                    continue;
                }

                Matcher trailing = TRAILING_AMOUNT.matcher(line);
                if (trailing.find() && !line.toUpperCase(Locale.ROOT).contains("REFID")) {
                    String descPart = line.substring(0, trailing.start()).trim();
                    if (!descPart.isEmpty()) {
                        curDesc.append(" ").append(descPart);
                    }
                    addCashRecord(out, curDate, curType, curDesc.toString(), depositColumn,
                            parseMoney(trailing.group(1)));
                    curDate = null;
                    curType = null;
                    curDesc.setLength(0);
                    continue;
                }

                curDesc.append(" ").append(line.trim());
            }
        }

        flushCashIfReady(out, curDate, curType, curDesc.toString(), depositColumn);
        return lines.size();
    }

    private static int parseOtherActivity(List<String> lines, int start, List<EtradeBrokerageRecord> out) {
        // Other Activity rows (e.g. option assignments) are non-cash and already
        // appear in the securities section, so they are intentionally not loaded.
        return lines.size();
    }

    private static EtradeBrokerageRecord buildSecuritiesRecord(List<String> buffer, String tailLine, Matcher tail,
            String section) {
        String txnType = tail.group(1);
        double amount = parseMoney(tail.group(4));
        boolean bought = "Bought".equalsIgnoreCase(txnType);
        double signedAmount = bought ? -Math.abs(amount) : Math.abs(amount);

        LocalDate tradeDate = null;
        StringBuilder desc = new StringBuilder();

        for (String line : buffer) {
            if (TRADE_DATE.matcher(line).matches()) {
                if (tradeDate == null) {
                    tradeDate = parseDate(line);
                }
                continue;
            }
            if (TRADE_TIME.matcher(line).matches()) {
                continue;
            }

            Matcher datePrefix = TRADE_DATE_PREFIX.matcher(line);
            if (datePrefix.matches() && tradeDate == null) {
                tradeDate = parseDate(datePrefix.group(1));
                String rest = datePrefix.group(2).trim();
                if (!rest.isEmpty()) {
                    appendDesc(desc, rest);
                }
                continue;
            }

            Matcher settlement = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2})\\s+(.+)$").matcher(line);
            if (settlement.find()) {
                String rest = settlement.group(2).trim();
                if (!rest.isEmpty()) {
                    appendDesc(desc, rest);
                }
                continue;
            }

            appendDesc(desc, line.trim());
        }

        String prefix = tailLine.substring(0, tail.start(1)).trim();
        if (!prefix.isEmpty()) {
            Matcher settlement = Pattern.compile("^(\\d{2}/\\d{2}/\\d{2})\\s+(.+)$").matcher(prefix);
            if (settlement.find()) {
                String rest = settlement.group(2).trim();
                if (!rest.isEmpty()) {
                    appendDesc(desc, rest);
                }
            } else {
                appendDesc(desc, prefix);
            }
        }

        if (tradeDate == null) {
            return null;
        }

        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(tradeDate);
        rec.setSection(section);
        rec.setTransactionType(txnType);
        rec.setDescription(desc.length() == 0 ? txnType : desc.toString().replaceAll("\\s{2,}", " ").trim());
        rec.setAmount(signedAmount);
        return rec;
    }

    private static void flushDividendIfReady(List<EtradeBrokerageRecord> out, LocalDate date, String type, String desc) {
        if (date == null || type == null) {
            return;
        }
        String clean = desc.trim();
        if (clean.isEmpty()) {
            return;
        }
        Matcher trailing = TRAILING_AMOUNT.matcher(clean);
        if (trailing.find()) {
            String descPart = clean.substring(0, trailing.start()).trim();
            double amount = parseMoney(trailing.group(1));
            addDividendRecord(out, date, type, descPart, null, amount);
        }
    }

    private static void flushCashIfReady(List<EtradeBrokerageRecord> out, LocalDate date, String type, String desc,
            boolean depositColumn) {
        if (date == null || type == null) {
            return;
        }
        String clean = desc.trim();
        if (clean.isEmpty()) {
            return;
        }
        Matcher trailing = TRAILING_AMOUNT.matcher(clean);
        if (trailing.find()) {
            String descPart = clean.substring(0, trailing.start()).trim();
            addCashRecord(out, date, type, descPart, depositColumn, parseMoney(trailing.group(1)));
        }
    }

    private static void addDividendRecord(List<EtradeBrokerageRecord> out, LocalDate date, String type, String desc,
            String symbol, double signedAmount) {
        String cleanDesc = desc.replaceAll("\\s{2,}", " ").trim();
        if (!cleanDesc.isEmpty()) {
            cleanDesc = type + " - " + cleanDesc;
        } else {
            cleanDesc = type;
        }
        if (symbol != null && !cleanDesc.contains(symbol)) {
            cleanDesc = cleanDesc + " " + symbol;
        }

        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(date);
        rec.setSection("Dividends & Interest");
        rec.setTransactionType(type);
        rec.setSymbol(symbol);
        rec.setDescription(cleanDesc.trim());
        rec.setAmount(signedAmount);
        out.add(rec);
    }

    private static void addCashRecord(List<EtradeBrokerageRecord> out, LocalDate date, String type, String desc,
            boolean depositColumn, double amount) {
        // Only external transfers belong in the ledger from this section.
        // Adjustment / Credit / Mark to Mkt rows are skipped.
        if (type == null || !"Transfer".equalsIgnoreCase(type)) {
            return;
        }

        String cleanDesc = desc.replaceAll("\\s{2,}", " ").trim();
        if (!cleanDesc.isEmpty()) {
            cleanDesc = type + " - " + cleanDesc;
        } else {
            cleanDesc = type;
        }

        if (isInternalHousekeeping(type, cleanDesc)) {
            return;
        }

        double signed = depositColumn ? Math.abs(amount) : -Math.abs(amount);

        EtradeBrokerageRecord rec = new EtradeBrokerageRecord();
        rec.setTransactionDate(date);
        rec.setSection("Withdrawals & Deposits");
        rec.setTransactionType(type);
        rec.setDescription(cleanDesc.trim());
        rec.setAmount(signed);
        out.add(rec);
    }

    /**
     * Internal E*TRADE bookkeeping that shuffles cash between sub-accounts or
     * records unrealized P&amp;L. These do not represent real cash entering or
     * leaving the brokerage.
     */
    static boolean isInternalHousekeeping(String transactionType, String description) {
        if (description == null || description.isBlank()) {
            return false;
        }

        String upper = description.toUpperCase(Locale.ROOT);

        if ("Mark to Mkt".equalsIgnoreCase(transactionType) || upper.contains("MARK TO MARKET")) {
            return true;
        }

        return upper.contains("TRNSFR FROM CASH TO MARGIN")
                || upper.contains("TRNSFR FROM MARGIN TO CASH")
                || upper.contains("TFR MARGIN TO CASH");
    }

    private static void appendDesc(StringBuilder desc, String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (desc.length() > 0) {
            desc.append(' ');
        }
        desc.append(part);
    }

    private static boolean isSectionBoundary(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("TOTAL SECURITIES ACTIVITY")
                || upper.startsWith("DIVIDENDS & INTEREST ACTIVITY")
                || upper.startsWith("WITHDRAWALS & DEPOSITS")
                || upper.startsWith("OTHER ACTIVITY")
                || upper.startsWith("NET DIVIDENDS & INTEREST ACTIVITY")
                || upper.startsWith("NET WITHDRAWALS & DEPOSITS");
    }

    private static boolean isSecuritiesNoise(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("SECURITIES PURCHASED OR SOLD")
                || upper.startsWith("UNSETTLED TRADES")
                || upper.equals("TRADE")
                || upper.equals("DATE")
                || upper.equals("SETTLEMENT")
                || upper.equals("TYPE")
                || upper.equals("CUSIP")
                || upper.startsWith("DESCRIPTION")
                || upper.startsWith("TRANSACTION")
                || upper.startsWith("QUANTITY")
                || upper.equals("AMOUNT")
                || upper.equals("PURCHASED")
                || upper.equals("SOLD")
                || upper.startsWith("SYMBOL/")
                || upper.startsWith("CUSIP")
                || upper.startsWith("PRICE")
                || upper.startsWith("PAGE ")
                || upper.startsWith("ACCOUNT NUMBER:")
                || upper.contains("STATEMENT PERIOD");
    }

    private static boolean isDividendNoise(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("DIVIDENDS & INTEREST ACTIVITY")
                || upper.startsWith("DATE")
                || upper.startsWith("TRANSACTION")
                || upper.startsWith("DESCRIPTION")
                || upper.startsWith("AMOUNT")
                || upper.startsWith("DEBITED")
                || upper.startsWith("CREDITED")
                || upper.startsWith("SYMBOL/")
                || upper.startsWith("CUSIP")
                || upper.startsWith("PAGE ")
                || upper.startsWith("ACCOUNT NUMBER:")
                || upper.contains("STATEMENT PERIOD")
                || upper.startsWith("TOTAL DIVIDENDS & INTEREST ACTIVITY")
                || upper.startsWith("NET DIVIDENDS & INTEREST ACTIVITY");
    }

    private static boolean isCashNoise(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        return upper.startsWith("WITHDRAWALS & DEPOSITS")
                || upper.startsWith("DATE")
                || upper.startsWith("TRANSACTION")
                || upper.startsWith("DESCRIPTION")
                || upper.startsWith("WITHDRAWALS")
                || upper.startsWith("DEPOSITS")
                || upper.startsWith("PAGE ")
                || upper.startsWith("ACCOUNT NUMBER:")
                || upper.contains("STATEMENT PERIOD")
                || upper.startsWith("NET WITHDRAWALS & DEPOSITS");
    }

    private static String sectionName(String line) {
        return line.toUpperCase(Locale.ROOT).startsWith("UNSETTLED") ? "Unsettled Trades" : "Securities";
    }

    private static List<String> normalizeLines(String text) {
        String[] rawLines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = (l == null) ? "" : l.trim();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }
        return lines;
    }

    private static int indexOfContains(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).toUpperCase(Locale.ROOT).contains(needle.toUpperCase(Locale.ROOT))) {
                return i;
            }
        }
        return -1;
    }

    private static LocalDate parseDate(String s) {
        return LocalDate.parse(s.trim(), DATE_FMT);
    }

    private static double parseMoney(String s) {
        if (s == null) {
            return 0.0;
        }
        String t = s.trim().replace(",", "").replace("$", "");
        if (t.isEmpty()) {
            return 0.0;
        }
        return Double.parseDouble(t);
    }

    private static String extractText(Path pdfPath) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: EtradeBrokerageRecord <statement.pdf>");
            return;
        }

        List<EtradeBrokerageRecord> recs = readTransactionsFromPdf(Path.of(args[0]));
        for (EtradeBrokerageRecord r : recs) {
            System.out.println(r);
        }
        System.out.println("Total records: " + recs.size());
    }
}
