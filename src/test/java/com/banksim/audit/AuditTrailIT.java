package com.banksim.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banksim.AbstractIntegrationTest;
import com.banksim.account.dto.AccountResponse;
import com.banksim.common.InsufficientFundsException;
import com.banksim.user.Role;
import com.banksim.user.User;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class AuditTrailIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private long count(String type, String outcome, Long actor) {
        return jdbc.queryForObject("select count(*) from audit_log where event_type = ? and outcome = ? and actor_user_id is not distinct from ?",
                Long.class, type, outcome, actor);
    }

    private String token(User user) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"Passw0rd!x\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    void failedTransferIsAuditedAsRejected() {
        User user = newUser(Role.USER);
        AccountResponse from = accountWithFunds(user, "10.00");
        AccountResponse to = accountWithFunds(newUser(Role.USER), "0");
        assertThrows(InsufficientFundsException.class, () ->
                transferService.transfer(user.getId(), from.id(), to.accountNumber(), new BigDecimal("50.00"), null, null));
        assertEquals(1, count("TRANSFER_REJECTED", "FAILURE", user.getId()));
    }

    @Test
    void completedTransferIsAudited() {
        User user = newUser(Role.USER);
        AccountResponse from = accountWithFunds(user, "100.00");
        AccountResponse to = accountWithFunds(newUser(Role.USER), "0");
        transferService.transfer(user.getId(), from.id(), to.accountNumber(), new BigDecimal("25.00"), null, null);
        assertEquals(1, count("TRANSFER_COMPLETED", "SUCCESS", user.getId()));
    }

    @Test
    void loginSuccessAndFailureAreAudited() throws Exception {
        User user = newUser(Role.USER);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"nope-nope\"}")).andExpect(status().isUnauthorized());
        token(user);
        Long failures = jdbc.queryForObject("select count(*) from audit_log where event_type='LOGIN_FAILURE' and target = ?", Long.class, "user:" + user.getEmail());
        Long successes = jdbc.queryForObject("select count(*) from audit_log where event_type='LOGIN_SUCCESS' and target = ?", Long.class, "user:" + user.getEmail());
        assertEquals(1L, failures);
        assertEquals(1L, successes);
    }

    @Test
    void auditRowsCannotBeChangedOrDeleted() {
        User user = newUser(Role.USER);
        accountWithFunds(user, "1.00");
        jdbc.update("insert into audit_log(event_type, outcome) values ('REGISTER','SUCCESS')");
        assertThrows(DataAccessException.class, () -> jdbc.update("update audit_log set outcome = 'FAILURE'"));
        assertThrows(DataAccessException.class, () -> jdbc.update("delete from audit_log"));
    }

    @Test
    void onlyAdminsCanReadTheAuditTrail() throws Exception {
        String userToken = token(newUser(Role.USER));
        String adminToken = token(newUser(Role.ADMIN));
        mvc.perform(get("/api/v1/admin/audit-events").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/audit-events").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/audit-events")).andExpect(status().isUnauthorized());
    }
}
