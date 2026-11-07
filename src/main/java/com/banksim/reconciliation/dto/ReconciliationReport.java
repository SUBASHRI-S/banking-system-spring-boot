package com.banksim.reconciliation.dto;

import java.time.Instant;
import java.util.List;

public record ReconciliationReport(Instant ranAt, long checkedAccounts, boolean healthy,
                                   List<ReconciliationMismatch> mismatches) {
}
