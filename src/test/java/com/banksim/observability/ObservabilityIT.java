package com.banksim.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banksim.AbstractIntegrationTest;
import com.banksim.account.dto.AccountResponse;
import com.banksim.common.InsufficientFundsException;
import com.banksim.user.Role;
import com.banksim.user.User;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ObservabilityIT extends AbstractIntegrationTest {

    @Autowired
    MeterRegistry registry;
    @Autowired
    MockMvc mvc;

    private double count(String outcome, String reason) {
        var c = registry.find("banksim.transfers").tag("outcome", outcome).tag("reason", reason).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void transferOutcomesAreCounted() {
        User user = newUser(Role.USER);
        AccountResponse from = accountWithFunds(user, "100.00");
        AccountResponse to = accountWithFunds(newUser(Role.USER), "0");
        double completed = count("completed", "none");
        double rejected = count("rejected", "INSUFFICIENT_FUNDS");

        transferService.transfer(user.getId(), from.id(), to.accountNumber(), new BigDecimal("10.00"), null, null);
        assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(user.getId(), from.id(), to.accountNumber(), new BigDecimal("999.00"), null, null));

        assertEquals(completed + 1, count("completed", "none"));
        assertEquals(rejected + 1, count("rejected", "INSUFFICIENT_FUNDS"));
        assertNotNull(registry.find("banksim.transfer.duration").timer());
    }

    @Test
    void requestIdIsEchoedAndAppearsInErrors() throws Exception {
        mvc.perform(get("/api/v1/accounts").header("X-Request-Id", "demo-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "demo-123"));
        mvc.perform(get("/api/v1/accounts").header("X-Request-Id", "bad id with spaces!"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void metricsEndpointIsNotPublic() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
