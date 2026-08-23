package com.stockapp.domain.screening;

import com.stockapp.domain.stock.DailyPriceFinalizationExecutionStatus;
import com.stockapp.domain.stock.DailyPriceFinalizationRecoveryService;
import com.stockapp.domain.stock.KrxTradingCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class OperationalScreeningEvaluationDateResolver {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final KrxTradingCalendar tradingCalendar;
    private final DailyPriceFinalizationRecoveryService recoveryService;
    private final Clock clock;

    public OperationalScreeningReadinessResult resolve() {
        LocalDate today = LocalDate.now(clock.withZone(KOREA_ZONE));
        if (!tradingCalendar.isTradingDay(today)) {
            return OperationalScreeningReadinessResult.notTradingDay(today);
        }

        LocalDate expectedEvaluationDate =
                tradingCalendar.previousTradingDay(today);
        boolean ready = recoveryService.findExecution(expectedEvaluationDate)
                .filter(execution -> execution.status()
                        == DailyPriceFinalizationExecutionStatus.COMPLETED)
                .filter(execution -> execution.ready())
                .isPresent();
        if (!ready) {
            return OperationalScreeningReadinessResult.finalizationNotReady(
                    today, expectedEvaluationDate);
        }
        return OperationalScreeningReadinessResult.ready(
                today, expectedEvaluationDate);
    }
}
