package com.stockbatch.kismasterreconciliation;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kis.master.reconciliation")
public record KisMasterReconciliationProperties(String mode) {
}
