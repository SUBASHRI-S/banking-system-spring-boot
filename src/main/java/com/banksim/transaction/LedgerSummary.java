package com.banksim.transaction;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Small streams/collections example: total amount per entry type. */
public final class LedgerSummary {

    private LedgerSummary() {
    }

    public static Map<EntryType, BigDecimal> totalsByType(List<LedgerEntry> entries) {
        return entries.stream().collect(Collectors.groupingBy(
                LedgerEntry::getEntryType,
                () -> new EnumMap<>(EntryType.class),
                Collectors.reducing(BigDecimal.ZERO, LedgerEntry::getAmount, BigDecimal::add)));
    }
}
