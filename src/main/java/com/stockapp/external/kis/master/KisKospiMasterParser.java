package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.MarketType;
import org.springframework.stereotype.Component;

@Component
public class KisKospiMasterParser extends AbstractKisMasterParser {

    private static final int[] FIELD_WIDTHS = {
            2, 1, 4, 4, 4, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 9, 5, 5, 1, 1, 1, 2, 1, 1,
            1, 2, 2, 2, 3, 1, 3, 12, 12, 8,
            15, 21, 2, 7, 1, 1, 1, 1, 1, 9,
            9, 9, 5, 9, 8, 9, 3, 1, 1, 1
    };

    @Override
    public MarketType market() {
        return MarketType.KOSPI;
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
        return 54;
    }

    @Override
    protected int etpProductIndex() {
        return 12;
    }

    @Override
    protected int spacIndex() {
        return 19;
    }

    @Override
    protected int suspendedIndex() {
        return 34;
    }

    @Override
    protected int liquidationIndex() {
        return 35;
    }

    @Override
    protected int managedIssueIndex() {
        return 36;
    }

    @Override
    protected int listingDateIndex() {
        return 49;
    }
}
