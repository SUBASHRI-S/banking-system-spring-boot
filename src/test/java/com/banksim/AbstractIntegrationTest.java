package com.banksim;

import com.banksim.account.AccountRepository;
import com.banksim.account.AccountService;
import com.banksim.account.AccountType;
import com.banksim.account.dto.AccountResponse;
import com.banksim.transaction.LedgerEntryRepository;
import com.banksim.transfer.TransferRepository;
import com.banksim.transfer.TransferService;
import com.banksim.user.Role;
import com.banksim.user.User;
import com.banksim.user.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for every test that needs the database. Uses a REAL PostgreSQL 16 in Docker (Testcontainers),
 * not H2, because locking and constraints behave differently. One container is shared by all test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        // the JDBC driver sends the JVM time zone to PostgreSQL; some legacy names (Asia/Calcutta) are rejected
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> "integration-test-secret-0123456789-abcdef");
        registry.add("app.seed.admin-password", () -> "");
    }

    @Autowired
    protected UserRepository users;
    @Autowired
    protected AccountRepository accountRepository;
    @Autowired
    protected LedgerEntryRepository ledgerRepository;
    @Autowired
    protected TransferRepository transferRepository;
    @Autowired
    protected AccountService accountService;
    @Autowired
    protected TransferService transferService;
    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected User newUser(Role role) {
        return users.save(new User("user-" + UUID.randomUUID() + "@example.com",
                passwordEncoder.encode("Passw0rd!x"), "Test User", role));
    }

    protected AccountResponse accountWithFunds(User owner, String funds) {
        AccountResponse account = accountService.openAccount(owner.getId(), AccountType.SAVINGS);
        BigDecimal amount = new BigDecimal(funds);
        if (amount.signum() > 0) {
            accountService.deposit(owner.getId(), account.id(), amount);
        }
        return account;
    }

    protected BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }
}
