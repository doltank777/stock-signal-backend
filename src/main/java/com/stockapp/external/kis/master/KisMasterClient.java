package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.springframework.stereotype.Component;

@Component
public class KisMasterClient {

    private final KisMasterDownloader downloader;
    private final KisMasterArchiveReader archiveReader;
    private final KisMasterParserRouter parserRouter;

    public KisMasterClient(
            KisMasterDownloader downloader,
            KisMasterArchiveReader archiveReader,
            KisMasterParserRouter parserRouter
    ) {
        this.downloader = downloader;
        this.archiveReader = archiveReader;
        this.parserRouter = parserRouter;
    }

    public KisMasterParseResult downloadAndParse(MarketType market) {
        byte[] archive = downloader.download(market);
        byte[] content = archiveReader.readMasterEntry(market, archive);
        return parserRouter.parse(market, content);
    }
}
