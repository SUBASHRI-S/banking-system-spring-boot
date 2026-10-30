package com.banksim.account;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByIdAsc(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);

    /** Does not load the entity, so no stale copy ends up in the persistence context before locking. */
    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByAccountNumber(String accountNumber);

    /** Plain read of the destination id (scalar, no entity is loaded). */
    @Query("select a.id from Account a where a.accountNumber = :number")
    Optional<Long> findIdByAccountNumber(@Param("number") String number);

    /** SELECT ... FOR UPDATE. Must be the first read of this row in the transaction so the state is fresh. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
