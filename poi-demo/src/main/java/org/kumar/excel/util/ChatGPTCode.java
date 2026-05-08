package org.kumar.excel.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Column-wise statistics from an Excel .xlsx file using Apache POI.
 *
 * Usage:
 *   java ColumnWiseExcelStats <path-to-xlsx> [sheetName] [--no-header]
 *
 * Defaults:
 *   - If sheetName omitted, the first sheet is used.
 *   - Assumes first row contains headers unless --no-header is provided.
 * Output:
 *   - Prints a table to console
 *   - Writes CSV "column_stats.csv" next to the input file
 */
public class ChatGPTCode {

    static class Welford {
        long n = 0;
        double mean = 0.0;
        double m2 = 0.0;

        void add(double x) {
            n++;
            double delta = x - mean;
            mean += delta / n;
            double delta2 = x - mean;
            m2 += delta * delta2;
        }

        double count() { return n; }
        double avg() { return n > 0 ? mean : Double.NaN; }
        double variance() { return n > 1 ? m2 / (n - 1) : Double.NaN; }
        double stddev() { return n > 1 ? Math.sqrt(variance()) : Double.NaN; }
    }

    static class ColumnStats {
        String header;
        int index;

        long totalCells = 0;
        long blanks = 0;
        long errors = 0;

        long numericCount = 0;
        long stringCount = 0;
        long booleanCount = 0;
        long dateCount = 0;

        // Numeric stats
        Double numMin = null;
        Double numMax = null;
        Welford w = new Welford();
        List<Double> numericForMedian = new ArrayList<>();

        // Date stats
        Date dateMin = null;
        Date dateMax = null;

        // Strings
        Set<String> distinctStrings = new HashSet<>();

        ColumnStats(int index, String header) {
            this.index = index;
            this.header = header == null ? ("Column " + (index + 1)) : header.trim();
        }

        void update(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
            totalCells++;

            if (cell == null) {
                blanks++;
                return;
            }

            CellType type = cell.getCellType();
            if (type == CellType.FORMULA) {
                // Evaluate formula to a concrete type, but don't mutate the sheet
                type = evaluator.evaluateFormulaCell(cell);
            }

            switch (type) {
                case BLANK:
                    blanks++;
                    break;
                case BOOLEAN:
                    booleanCount++;
                    break;
                case ERROR:
                    errors++;
                    break;
                case STRING: {
                    stringCount++;
                    String s = formatter.formatCellValue(cell);
                    if (s != null) distinctStrings.add(s);
                    break;
                }
                case NUMERIC: {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        dateCount++;
                        Date d = cell.getDateCellValue();
                        if (dateMin == null || d.before(dateMin)) dateMin = d;
                        if (dateMax == null || d.after(dateMax)) dateMax = d;
                    } else {
                        numericCount++;
                        double v = cell.getNumericCellValue();
                        if (numMin == null || v < numMin) numMin = v;
                        if (numMax == null || v > numMax) numMax = v;
                        w.add(v);
                        numericForMedian.add(v);
                    }
                    break;
                }
                default: {
                    // Fallback: treat as string via formatter (covers some odd cases)
                    stringCount++;
                    String s = formatter.formatCellValue(cell, evaluator);
                    if (s != null) distinctStrings.add(s);
                }
            }
        }

        String medianString() {
            if (numericForMedian.isEmpty()) return "";
            Collections.sort(numericForMedian);
            int n = numericForMedian.size();
            if (n % 2 == 1) {
                return String.valueOf(numericForMedian.get(n / 2));
            } else {
                double a = numericForMedian.get(n / 2 - 1);
                double b = numericForMedian.get(n / 2);
                return String.valueOf((a + b) / 2.0);
            }
        }

        String dateFmt(Date d) {
            if (d == null) return "";
            return new SimpleDateFormat("yyyy-MM-dd").format(d);
        }

        List<String> toRow() {
            return Arrays.asList(
                String.valueOf(index + 1),
                header,
                String.valueOf(totalCells),
                String.valueOf(blanks),
                String.valueOf(errors),
                String.valueOf(numericCount),
                String.valueOf(stringCount),
                String.valueOf(booleanCount),
                String.valueOf(dateCount),
                numMin == null ? "" : String.valueOf(numMin),
                numMax == null ? "" : String.valueOf(numMax),
                w.count() == 0 ? "" : String.valueOf(w.avg()),
                w.count() <= 1 ? "" : String.valueOf(w.stddev()),
                numericForMedian.isEmpty() ? "" : medianString(),
                dateFmt(dateMin),
                dateFmt(dateMax),
                String.valueOf(distinctStrings.size())
            );
        }
    }

    public static void main(String[] args) throws IOException, InvalidFormatException {
        if (args.length < 1) {
            System.err.println("Usage: java ColumnWiseExcelStats <path-to-xlsx> [sheetName] [--no-header]");
            System.exit(1);
        }

        Path xlsx = Paths.get(args[0]);
        if (!Files.exists(xlsx)) {
            System.err.println("File not found: " + xlsx);
            System.exit(2);
        }

        String sheetName = null;
        boolean hasHeader = true;
        for (int i = 1; i < args.length; i++) {
            if ("--no-header".equalsIgnoreCase(args[i])) {
                hasHeader = false;
            } else {
                sheetName = args[i];
            }
        }

        try (InputStream in = Files.newInputStream(xlsx);
             Workbook wb = WorkbookFactory.create(in)) {

            Sheet sheet = (sheetName == null) ? wb.getSheetAt(0) : wb.getSheet(sheetName);
            if (sheet == null) {
                System.err.println("Sheet not found: " + sheetName);
                System.exit(3);
            }

            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = wb.getCreationHelper().createFormulaEvaluator();

            // Determine header row and number of columns to scan
            int firstRowIdx = sheet.getFirstRowNum();
            int startRow = firstRowIdx;
            Row headerRow = sheet.getRow(firstRowIdx);
            if (hasHeader) {
                startRow = firstRowIdx + 1;
            }

            int colCount = 0;
            if (headerRow != null) {
                colCount = headerRow.getLastCellNum(); // one-based count
            }
            // If header row is empty or --no-header, infer from max lastCellNum across some initial rows
            if (colCount <= 0) {
                int max = 0;
                int limit = Math.min(sheet.getLastRowNum(), firstRowIdx + 200);
                for (int r = firstRowIdx; r <= limit; r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getLastCellNum() > max) {
                        max = row.getLastCellNum();
                    }
                }
                colCount = Math.max(0, max);
            }

            if (colCount == 0) {
                System.err.println("No columns detected.");
                System.exit(4);
            }

            // Build ColumnStats list with headers (or generic names)
            List<ColumnStats> cols = new ArrayList<>();
            for (int c = 0; c < colCount; c++) {
                String header = null;
                if (hasHeader && headerRow != null) {
                    Cell h = headerRow.getCell(c);
                    header = (h == null) ? null : new DataFormatter().formatCellValue(h);
                }
                cols.add(new ColumnStats(c, header));
            }

            // Iterate rows
            int lastRow = sheet.getLastRowNum();
            for (int r = startRow; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                for (int c = 0; c < colCount; c++) {
                    Cell cell = (row == null) ? null : row.getCell(c);
                    cols.get(c).update(cell, formatter, evaluator);
                }
            }

            // Print table to console
            String[] headers = {
                "Col#", "Header", "Total", "Blanks", "Errors",
                "Numeric", "Strings", "Booleans", "Dates",
                "NumMin", "NumMax", "Avg", "StdDev", "Median",
                "DateMin", "DateMax", "DistinctStrings"
            };

            printTable(headers, cols);

            // Write CSV next to the file
            Path csvOut = xlsx.getParent() == null
                ? Paths.get("column_stats.csv")
                : xlsx.getParent().resolve("column_stats.csv");
            writeCsv(csvOut, headers, cols);
            System.out.println("\nWrote: " + csvOut.toAbsolutePath());
        }
    }

    private static void printTable(String[] headers, List<ColumnStats> cols) {
        // Simple fixed-width printing
        int[] widths = new int[headers.length];
        List<List<String>> rows = new ArrayList<>();
        rows.add(Arrays.asList(headers));
        for (ColumnStats cs : cols) rows.add(cs.toRow());
        // compute widths
        for (List<String> row : rows) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], row.get(i) == null ? 0 : row.get(i).length());
            }
        }
        // print
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int i = 0; i < row.size(); i++) {
                String s = row.get(i) == null ? "" : row.get(i);
                System.out.print(pad(s, widths[i] + 2));
            }
            System.out.println();
            if (r == 0) {
                for (int i = 0; i < widths.length; i++) {
                    System.out.print(pad(repeat('-', widths[i]), widths[i] + 2));
                }
                System.out.println();
            }
        }
    }

    private static String pad(String s, int w) {
        StringBuilder b = new StringBuilder(s == null ? "" : s);
        while (b.length() < w) b.append(' ');
        return b.toString();
    }

    private static String repeat(char ch, int n) {
        char[] arr = new char[n];
        Arrays.fill(arr, ch);
        return new String(arr);
    }

    private static void writeCsv(Path out, String[] headers, List<ColumnStats> cols) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(out)) {
            bw.write(String.join(",", csvEscape(Arrays.asList(headers))));
            bw.newLine();
            for (ColumnStats cs : cols) {
                bw.write(String.join(",", csvEscape(cs.toRow())));
                bw.newLine();
            }
        }
    }

    private static List<String> csvEscape(List<String> fields) {
        List<String> out = new ArrayList<>(fields.size());
        for (String f : fields) {
            if (f == null) f = "";
            boolean mustQuote = f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r");
            if (mustQuote) {
                f = "\"" + f.replace("\"", "\"\"") + "\"";
            }
            out.add(f);
        }
        return out;
    }
}

