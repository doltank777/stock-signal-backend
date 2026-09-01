package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractKisMasterParser implements KisMasterParser {

    private static final Charset MASTER_CHARSET = Charset.forName("MS949");
    private static final DateTimeFormatter LISTING_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final int SHORT_CODE_WIDTH = 9;
    private static final int STANDARD_CODE_WIDTH = 12;
    private static final int PREFIX_IDENTITY_WIDTH = SHORT_CODE_WIDTH + STANDARD_CODE_WIDTH;

    @Override
    public final KisMasterParseResult parse(byte[] masterContent) {
        if (masterContent == null || masterContent.length == 0) {
            throw new KisMasterException("KIS Master content is empty: " + market());
        }

        List<KisMasterRawRecord> records = new ArrayList<>();
        var decoder = MASTER_CHARSET.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(masterContent), decoder))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                records.add(parseLine(line, lineNumber));
            }
        } catch (IOException exception) {
            throw new KisMasterException("Failed to decode KIS Master: " + market(), exception);
        }

        Map<String, Integer> counts = new HashMap<>();
        int blankCodes = 0;
        for (KisMasterRawRecord record : records) {
            if (record.shortCode().isBlank()) {
                blankCodes++;
            }
            counts.merge(record.shortCode(), 1, Integer::sum);
        }
        int duplicates = counts.values().stream()
                .mapToInt(count -> Math.max(0, count - 1))
                .sum();
        return new KisMasterParseResult(
                records,
                new KisMasterParseSummary(records.size(), duplicates, blankCodes, 0));
    }

    private KisMasterRawRecord parseLine(String line, int lineNumber) {
        int suffixWidth = suffixWidth();
        if (line.length() < PREFIX_IDENTITY_WIDTH + 1 + suffixWidth) {
            throw invalid(lineNumber, "record is shorter than required fixed width");
        }

        int suffixStart = line.length() - suffixWidth;
        String prefix = line.substring(0, suffixStart);
        String shortCode = prefix.substring(0, SHORT_CODE_WIDTH).trim();
        String standardCode = prefix.substring(
                SHORT_CODE_WIDTH, PREFIX_IDENTITY_WIDTH).trim();
        String stockName = prefix.substring(PREFIX_IDENTITY_WIDTH).trim();
        requireText(shortCode, lineNumber, "shortCode");
        requireText(standardCode, lineNumber, "standardCode");
        requireText(stockName, lineNumber, "stockName");

        String rawSuffix = line.substring(suffixStart);
        List<String> fields = splitSuffix(rawSuffix, lineNumber);
        return new KisMasterRawRecord(
                market(),
                shortCode,
                standardCode,
                stockName,
                field(fields, securityGroupIndex()),
                field(fields, preferredStockIndex()),
                field(fields, etpProductIndex()),
                parseBoolean(fields, spacIndex(), "spac", lineNumber),
                parseBoolean(fields, suspendedIndex(), "suspended", lineNumber),
                parseBoolean(fields, liquidationIndex(), "liquidationTrading", lineNumber),
                parseBoolean(fields, managedIssueIndex(), "managedIssue", lineNumber),
                parseListingDate(field(fields, listingDateIndex()), lineNumber),
                rawSuffix,
                fields);
    }

    private List<String> splitSuffix(String suffix, int lineNumber) {
        List<String> fields = new ArrayList<>(fieldWidths().length);
        int offset = 0;
        for (int width : fieldWidths()) {
            if (offset + width > suffix.length()) {
                throw invalid(lineNumber, "suffix field exceeds record length");
            }
            fields.add(suffix.substring(offset, offset + width).trim());
            offset += width;
        }
        if (offset != suffix.length()) {
            throw invalid(lineNumber, "suffix width does not match parser contract");
        }
        return fields;
    }

    private boolean parseBoolean(
            List<String> fields,
            int index,
            String name,
            int lineNumber
    ) {
        String value = field(fields, index);
        return switch (value) {
            case "Y" -> true;
            case "N" -> false;
            default -> throw invalid(
                    lineNumber, name + " must be Y or N but was [" + value + "]");
        };
    }

    private LocalDate parseListingDate(String value, int lineNumber) {
        try {
            return LocalDate.parse(value, LISTING_DATE_FORMATTER);
        } catch (DateTimeException exception) {
            throw invalid(lineNumber, "listingDate must be yyyyMMdd but was [" + value + "]");
        }
    }

    private String field(List<String> fields, int index) {
        return index < 0 ? "" : fields.get(index);
    }

    private void requireText(String value, int lineNumber, String name) {
        if (value.isBlank()) {
            throw invalid(lineNumber, name + " must not be blank");
        }
    }

    private KisMasterException invalid(int lineNumber, String detail) {
        return new KisMasterException(
                "Invalid " + market() + " KIS Master record at line " + lineNumber + ": " + detail);
    }

    protected abstract int[] fieldWidths();

    protected abstract int securityGroupIndex();

    protected abstract int preferredStockIndex();

    protected abstract int etpProductIndex();

    protected abstract int spacIndex();

    protected abstract int suspendedIndex();

    protected abstract int liquidationIndex();

    protected abstract int managedIssueIndex();

    protected abstract int listingDateIndex();

    private int suffixWidth() {
        int total = 0;
        for (int width : fieldWidths()) {
            total += width;
        }
        return total;
    }
}
