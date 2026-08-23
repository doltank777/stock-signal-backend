package com.stockapp.domain.screening.metric;

import com.stockapp.domain.screening.ScreeningStockDataException;

public class OperationalScreeningDataMissingException
        extends ScreeningStockDataException {

    public OperationalScreeningDataMissingException(String message) {
        super(message, null);
    }
}
