package com.stockapp.domain.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "kis_master_sync_executions", indexes = @Index(
        name = "idx_kis_master_sync_status_finished",
        columnList = "status, finished_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KisMasterSyncExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KisMasterSyncExecutionStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "observed_at")
    private Instant observedAt;

    @Column(name = "kospi_parsed_row_count")
    private Integer kospiParsedRowCount;

    @Column(name = "kosdaq_parsed_row_count")
    private Integer kosdaqParsedRowCount;

    @Column(name = "total_parsed_row_count")
    private Integer totalParsedRowCount;

    @Column(name = "supported_instrument_count")
    private Integer supportedInstrumentCount;

    @Column(name = "unsupported_instrument_count")
    private Integer unsupportedInstrumentCount;

    @Column(name = "unknown_instrument_count")
    private Integer unknownInstrumentCount;

    @Column(name = "duplicate_short_code_count")
    private Integer duplicateShortCodeCount;

    @Column(name = "invalid_row_count")
    private Integer invalidRowCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public static KisMasterSyncExecution create(Instant startedAt) {
        if (startedAt == null) {
            throw new IllegalArgumentException("startedAt must not be null");
        }
        KisMasterSyncExecution execution = new KisMasterSyncExecution();
        execution.status = KisMasterSyncExecutionStatus.RUNNING;
        execution.startedAt = startedAt;
        return execution;
    }

    public void complete(
            KisMasterSyncExecutionCompletion completion,
            Instant finishedAt
    ) {
        requireRunning();
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        status = KisMasterSyncExecutionStatus.COMPLETED;
        this.finishedAt = finishedAt;
        observedAt = completion.observedAt();
        kospiParsedRowCount = completion.kospiParsedRowCount();
        kosdaqParsedRowCount = completion.kosdaqParsedRowCount();
        totalParsedRowCount = completion.totalParsedRowCount();
        supportedInstrumentCount = completion.supportedInstrumentCount();
        unsupportedInstrumentCount = completion.unsupportedInstrumentCount();
        unknownInstrumentCount = completion.unknownInstrumentCount();
        duplicateShortCodeCount = completion.duplicateShortCodeCount();
        invalidRowCount = completion.invalidRowCount();
        lastError = null;
    }

    public void fail(String error, Instant finishedAt) {
        requireRunning();
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        if (finishedAt == null) {
            throw new IllegalArgumentException("finishedAt must not be null");
        }
        status = KisMasterSyncExecutionStatus.FAILED;
        this.finishedAt = finishedAt;
        lastError = error;
    }

    private void requireRunning() {
        if (status != KisMasterSyncExecutionStatus.RUNNING) {
            throw new IllegalStateException("Master sync execution is not running");
        }
    }
}
