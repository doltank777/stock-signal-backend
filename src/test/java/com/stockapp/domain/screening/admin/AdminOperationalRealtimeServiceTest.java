package com.stockapp.domain.screening.admin;

import com.stockapp.domain.screening.LatestScreeningSnapshotRegistry;
import com.stockapp.domain.screening.realtime.*;
import com.stockapp.external.kis.ManagedRealtimeSubscriptionGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOperationalRealtimeServiceTest {

    @Mock OperationalMorningRunCoordinator coordinator;
    @Mock LatestScreeningSnapshotRegistry screeningRegistry;
    @Mock LatestOperationalRealtimeSelectionRegistry selectionRegistry;
    @Mock RealtimeWatchTargetRegistry targetRegistry;
    @Mock ManagedRealtimeSubscriptionGateway gateway;
    @Mock KrxRegularMarketSessionPolicy sessionPolicy;
    @Mock Environment environment;

    private AdminOperationalRealtimeService service;

    @BeforeEach
    void setUp() {
        service = new AdminOperationalRealtimeService(
                coordinator, screeningRegistry, selectionRegistry,
                targetRegistry, gateway,
                new OperationalRealtimeAutomationProperties(),
                sessionPolicy, environment);
    }

    @Test
    void initialStatusIsAvailableWithoutExecutingOperationalSideEffects() {
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 24, 8, 0, 0, 0,
                ZoneId.of("Asia/Seoul"));
        when(sessionPolicy.now()).thenReturn(now);
        when(coordinator.snapshot()).thenReturn(
                new OperationalMorningRunSnapshot(
                        LocalDate.of(2026, 8, 24),
                        OperationalMorningRunStatus.IDLE, 0,
                        Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty()));
        when(screeningRegistry.findLatest()).thenReturn(Optional.empty());
        when(selectionRegistry.findLatest()).thenReturn(Optional.empty());
        when(targetRegistry.findAll()).thenReturn(Map.of());
        when(gateway.currentActiveStockCodes()).thenReturn(Set.of());
        when(environment.getProperty(
                "operational-screening.realtime.morning.enabled",
                Boolean.class, false)).thenReturn(false);

        var status = service.getStatus();

        assertThat(status.currentKst()).isEqualTo(now);
        assertThat(status.morning().status())
                .isEqualTo(OperationalMorningRunStatus.IDLE);
        assertThat(status.screening().available()).isFalse();
        assertThat(status.desired().available()).isFalse();
        assertThat(status.applied().registryCount()).isZero();
        assertThat(status.applied().physicalCount()).isZero();
        assertThat(status.automation().morningEnabled()).isFalse();
        verify(coordinator).snapshot();
    }
}
