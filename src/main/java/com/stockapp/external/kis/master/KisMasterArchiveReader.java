package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Component
public class KisMasterArchiveReader {

    public byte[] readMasterEntry(MarketType market, byte[] archive) {
        KisMasterMarketSpec spec = KisMasterMarketSpec.from(market);
        if (archive == null || archive.length == 0) {
            throw new KisMasterException("KIS Master ZIP is empty: " + market);
        }
        if (archive.length < 4
                || archive[0] != 'P'
                || archive[1] != 'K'
                || archive[2] != 3
                || archive[3] != 4) {
            throw new KisMasterException("Invalid KIS Master ZIP: " + market);
        }

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (spec.entryName().equals(entry.getName())) {
                    if (entry.isDirectory()) {
                        throw new KisMasterException(
                                "KIS Master entry is a directory: " + spec.entryName());
                    }
                    ByteArrayOutputStream content = new ByteArrayOutputStream();
                    zip.transferTo(content);
                    byte[] bytes = content.toByteArray();
                    if (bytes.length == 0) {
                        throw new KisMasterException(
                                "KIS Master entry is empty: " + spec.entryName());
                    }
                    return bytes;
                }
            }
        } catch (ZipException exception) {
            throw new KisMasterException("Invalid KIS Master ZIP: " + market, exception);
        } catch (IOException exception) {
            throw new KisMasterException("Failed to read KIS Master ZIP: " + market, exception);
        }
        throw new KisMasterException(
                "Expected KIS Master entry is missing: " + spec.entryName());
    }
}
