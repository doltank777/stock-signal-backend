package com.stockapp.external.kis.master;

import com.stockapp.domain.stock.KisMasterSyncExecution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KisMasterReconciliationServiceTest {

    @Test
    void recordsFailureAfterTransactionalPublisherRollsBack() {
        KisMasterReconciliationPublisher publisher =
                mock(KisMasterReconciliationPublisher.class);
        KisMasterSyncExecutionFailureRecorder failureRecorder =
                mock(KisMasterSyncExecutionFailureRecorder.class);
        KisMasterReconciliationService service =
                new KisMasterReconciliationService(publisher, failureRecorder);
        KisMasterSnapshot kospi = mock(KisMasterSnapshot.class);
        KisMasterSnapshot kosdaq = mock(KisMasterSnapshot.class);
        KisMasterSyncExecution execution = mock(KisMasterSyncExecution.class);
        RuntimeException failure = new RuntimeException("persistence failed");
        when(execution.getId()).thenReturn(7L);
        when(publisher.publish(kospi, kosdaq, 7L)).thenThrow(failure);

        assertThatThrownBy(() -> service.reconcile(kospi, kosdaq, execution))
                .isSameAs(failure);

        verify(failureRecorder).record(7L, failure);
        verify(publisher).publish(same(kospi), same(kosdaq), same(7L));
    }
}
