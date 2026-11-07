package com.banksim.reconciliation;

import com.banksim.reconciliation.dto.ReconciliationMismatch;
import com.banksim.reconciliation.dto.ReconciliationReport;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BANK-FR-14: independent control that re-derives every balance from the ledger and reports any account where the
 * stored balance differs. In a healthy system the list is always empty. A non-empty list means data corruption,
 * a bug, or tampering, and should page someone.
 *
 * The comparison runs as ONE SQL statement, so it sees a single consistent snapshot even while transfers commit.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private static final String MISMATCH_SQL = """
            select t.id, t.account_number, t.balance, t.ledger_total
            from (
                select a.id, a.account_number, a.balance,
                       coalesce(sum(case when l.entry_type in ('DEPOSIT', 'TRANSFER_CREDIT')
                                         then l.amount else -l.amount end), 0) as ledger_total
                from accounts a
                left join ledger_entries l on l.account_id = a.id
                group by a.id, a.account_number, a.balance
            ) t
            where t.balance <> t.ledger_total
            order by t.id
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AtomicInteger lastMismatchCount = new AtomicInteger(0);

    public ReconciliationService(JdbcTemplate jdbc, Clock clock, MeterRegistry registry) {
        this.jdbc = jdbc;
        this.clock = clock;
        Gauge.builder("banksim.reconciliation.mismatches", lastMismatchCount, AtomicInteger::get)
                .description("Accounts whose balance differs from their ledger in the last reconciliation run")
                .register(registry);
    }

    @Transactional(readOnly = true)
    public ReconciliationReport run() {
        Long checked = jdbc.queryForObject("select count(*) from accounts", Long.class);
        List<ReconciliationMismatch> mismatches = jdbc.query(MISMATCH_SQL, (rs, rowNum) -> {
            var balance = rs.getBigDecimal("balance");
            var ledgerTotal = rs.getBigDecimal("ledger_total");
            return new ReconciliationMismatch(rs.getLong("id"), rs.getString("account_number"),
                    balance, ledgerTotal, balance.subtract(ledgerTotal));
        });
        lastMismatchCount.set(mismatches.size());
        return new ReconciliationReport(clock.instant(), checked == null ? 0 : checked,
                mismatches.isEmpty(), mismatches);
    }

    /** Nightly run (cron configurable). Logs at ERROR when a mismatch exists so alerting can pick it up. */
    @Scheduled(cron = "${app.reconciliation.cron:0 0 2 * * *}", zone = "UTC")
    public void scheduledRun() {
        try {
            ReconciliationReport report = run();
            if (report.healthy()) {
                log.info("Reconciliation OK: {} accounts checked, no mismatches", report.checkedAccounts());
            } else {
                log.error("Reconciliation FAILED: {} of {} accounts do not match their ledger: {}",
                        report.mismatches().size(), report.checkedAccounts(), report.mismatches());
            }
        } catch (RuntimeException e) {
            log.error("Reconciliation run could not complete", e);
        }
    }
}
