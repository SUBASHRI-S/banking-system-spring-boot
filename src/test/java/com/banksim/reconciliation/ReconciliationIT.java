package com.banksim.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banksim.AbstractIntegrationTest;
import com.banksim.account.dto.AccountResponse;
import com.banksim.reconciliation.dto.ReconciliationReport;
import com.banksim.user.Role;
import com.banksim.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class ReconciliationIT extends AbstractIntegrationTest {

    @Autowired
    ReconciliationService reconciliation;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mvc;

    @Test
    void tamperedBalanceIsDetectedAndRepairedBalanceIsClean() {
        User user = newUser(Role.USER);
        AccountResponse account = accountWithFunds(user, "100.00");
        AccountResponse other = accountWithFunds(newUser(Role.USER), "0");
        transferService.transfer(user.getId(), account.id(), other.accountNumber(), new BigDecimal("40.00"), null, null);

        assertTrue(reconciliation.run().mismatches().stream().noneMatch(m -> m.accountId().equals(account.id())),
                "untouched accounts must reconcile");

        jdbc.update("update accounts set balance = balance + 500 where id = ?", account.id());
        ReconciliationReport report = reconciliation.run();
        assertFalse(report.healthy());
        var mismatch = report.mismatches().stream().filter(m -> m.accountId().equals(account.id())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(mismatch.difference()));

        jdbc.update("update accounts set balance = balance - 500 where id = ?", account.id());
        assertTrue(reconciliation.run().mismatches().stream().noneMatch(m -> m.accountId().equals(account.id())));
    }

    @Test
    void endpointIsAdminOnly() throws Exception {
        String userToken = login(newUser(Role.USER));
        String adminToken = login(newUser(Role.ADMIN));
        mvc.perform(get("/api/v1/admin/reconciliation").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/reconciliation").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedAccounts").isNumber());
    }

    private String login(User user) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"Passw0rd!x\"}"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }
}
