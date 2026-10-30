package com.banksim.account;

import com.banksim.common.AccountFrozenException;
import com.banksim.common.ApiException;
import com.banksim.common.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.http.HttpStatus;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 12)
    private String accountNumber;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = new BigDecimal("0.00");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Account() {
    }

    public Account(Long userId, String accountNumber, AccountType type) {
        this.userId = userId;
        this.accountNumber = accountNumber;
        this.type = type;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    // ---- business rules live here, not in the controller (OOP evidence) ----

    /** Can this account send or receive money right now? */
    public boolean canTransact() {
        return status == AccountStatus.ACTIVE;
    }

    public void assertActive() {
        if (status == AccountStatus.FROZEN) {
            throw new AccountFrozenException();
        }
        if (status == AccountStatus.CLOSED) {
            throw new ApiException(HttpStatus.CONFLICT, "ACCOUNT_CLOSED", "Account closed", "The account is closed");
        }
    }

    /** The amount must already be validated (positive, 2 decimals). */
    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    /** Throws if the balance would go below zero, so the balance never becomes negative. */
    public void debit(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(balance, amount);
        }
        balance = balance.subtract(amount);
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public AccountType getType() {
        return type;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
