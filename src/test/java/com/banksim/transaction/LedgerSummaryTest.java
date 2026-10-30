package com.banksim.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LedgerSummaryTest {

    private static LedgerEntry entry(EntryType type, String amount) {
        return new LedgerEntry(1L, null, type, new BigDecimal(amount), BigDecimal.ZERO);
    }

    @Test
    void groupsAndSumsAmountsByType() {
        List<LedgerEntry> entries = List.of(
                entry(EntryType.DEPOSIT, "100.00"),
                entry(EntryType.DEPOSIT, "50.50"),
                entry(EntryType.WITHDRAWAL, "20.00"),
                entry(EntryType.TRANSFER_DEBIT, "30.00"),
                entry(EntryType.TRANSFER_DEBIT, "5.00"));

        Map<EntryType, BigDecimal> totals = LedgerSummary.totalsByType(entries);

        assertEquals(0, new BigDecimal("150.50").compareTo(totals.get(EntryType.DEPOSIT)));
        assertEquals(0, new BigDecimal("20.00").compareTo(totals.get(EntryType.WITHDRAWAL)));
        assertEquals(0, new BigDecimal("35.00").compareTo(totals.get(EntryType.TRANSFER_DEBIT)));
        assertFalse(totals.containsKey(EntryType.TRANSFER_CREDIT));
    }
}
