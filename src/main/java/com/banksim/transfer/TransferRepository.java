package com.banksim.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByFromAccountIdAndIdempotencyKey(Long fromAccountId, String idempotencyKey);

    long countByFromAccountId(Long fromAccountId);

    /** Total sent since the given moment. Returns null when nothing was sent. */
    @Query("select sum(t.amount) from Transfer t where t.fromAccountId = :accountId and t.createdAt >= :since")
    BigDecimal sumOutgoingSince(@Param("accountId") Long accountId, @Param("since") Instant since);
}
