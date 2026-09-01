package com.stockapp.external.kis.master;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class KisMasterFixture {

    private static final Charset MS949 = Charset.forName("MS949");

    private KisMasterFixture() {
    }

    static byte[] kospi(
            String shortCode,
            String standardCode,
            String name,
            String group,
            String etp,
            String preferred,
            String spac,
            String suspended,
            String liquidation,
            String managed,
            String listingDate
    ) {
        KisKospiMasterParser parser = new KisKospiMasterParser();
        String[] values = blankValues(parser.fieldWidths().length);
        values[parser.securityGroupIndex()] = group;
        values[parser.etpProductIndex()] = etp;
        values[parser.preferredStockIndex()] = preferred;
        values[parser.spacIndex()] = spac;
        values[parser.suspendedIndex()] = suspended;
        values[parser.liquidationIndex()] = liquidation;
        values[parser.managedIssueIndex()] = managed;
        values[parser.listingDateIndex()] = listingDate;
        return line(shortCode, standardCode, name, parser.fieldWidths(), values);
    }

    static byte[] kosdaq(
            String shortCode,
            String standardCode,
            String name,
            String group,
            String etp,
            String preferred,
            String spac,
            String suspended,
            String liquidation,
            String managed,
            String listingDate
    ) {
        KisKosdaqMasterParser parser = new KisKosdaqMasterParser();
        String[] values = blankValues(parser.fieldWidths().length);
        values[parser.securityGroupIndex()] = group;
        values[parser.etpProductIndex()] = etp;
        values[parser.preferredStockIndex()] = preferred;
        values[parser.spacIndex()] = spac;
        values[parser.suspendedIndex()] = suspended;
        values[parser.liquidationIndex()] = liquidation;
        values[parser.managedIssueIndex()] = managed;
        values[parser.listingDateIndex()] = listingDate;
        return line(shortCode, standardCode, name, parser.fieldWidths(), values);
    }

    static byte[] lines(byte[]... lines) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            for (byte[] line : lines) {
                output.write(line);
                output.write('\n');
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return output.toByteArray();
    }

    static byte[] zip(String entryName, byte[] content) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(content);
                zip.closeEntry();
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String[] blankValues(int length) {
        String[] values = new String[length];
        Arrays.fill(values, "");
        return values;
    }

    private static byte[] line(
            String shortCode,
            String standardCode,
            String name,
            int[] widths,
            String[] values
    ) {
        StringBuilder line = new StringBuilder();
        line.append(pad(shortCode, 9));
        line.append(pad(standardCode, 12));
        line.append(name);
        for (int index = 0; index < widths.length; index++) {
            line.append(pad(values[index], widths[index]));
        }
        return line.toString().getBytes(MS949);
    }

    private static String pad(String value, int width) {
        if (value.length() > width) {
            throw new IllegalArgumentException("Fixture value exceeds width: " + value);
        }
        return value + " ".repeat(width - value.length());
    }
}
