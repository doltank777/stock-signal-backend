package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.springframework.stereotype.Component;

@Component
public class KisKosdaqMasterParser extends AbstractKisMasterParser {

    private static final int[] FIELD_WIDTHS = {
            2, 1, 4, 4, 4, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 9, 5, 5, 1,
            1, 1, 2, 1, 1, 1, 2, 2, 2, 3,
            1, 3, 12, 12, 8, 15, 21, 2, 7, 1,
            1, 1, 1, 9, 9, 9, 5, 9, 8, 9,
            3, 1, 1, 1
    };

    @Override
    public MarketType market() {
        return MarketType.KOSDAQ;
    }

    @Override
    protected int[] fieldWidths() {
        return FIELD_WIDTHS;
    }

    @Override
    protected int securityGroupIndex() {
        return 0;
    }

    @Override
    protected int preferredStockIndex() {
        return 49;
    }

    @Override
    protected int etpProductIndex() {
        return 8;
    }

    @Override
    protected int spacIndex() {
        return 14;
    }

    @Override
    protected int suspendedIndex() {
        return 29;
    }

    @Override
    protected int liquidationIndex() {
        return 30;
    }

    @Override
    protected int managedIssueIndex() {
        return 31;
    }

    @Override
    protected int listingDateIndex() {
        return 44;
    }
}
