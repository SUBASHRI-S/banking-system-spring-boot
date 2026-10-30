package com.banksim;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banksim.user.Role;
import com.banksim.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** HTTP-level tests through the real security filter chain and a real PostgreSQL. */
class ApiFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD = "Str0ng#Pass1";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;

    // ---------- helpers ----------

    private String json(Object body) throws Exception {
        return mapper.writeValueAsString(body);
    }

    private String uniqueEmail() {
        return "api-" + UUID.randomUUID() + "@example.com";
    }

    private void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Api Tester", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private String registerAndLogin() throws Exception {
        String email = uniqueEmail();
        register(email);
        return login(email, PASSWORD);
    }

    private long openAccount(String token) throws Exception {
        String body = mvc.perform(post("/api/v1/accounts").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("type", "SAVINGS"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value("0.00"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private String accountNumber(String token, long id) throws Exception {
        String body = mvc.perform(get("/api/v1/accounts/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accountNumber");
    }

    private void deposit(String token, long id, String amount) throws Exception {
        mvc.perform(post("/api/v1/accounts/" + id + "/deposit").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", amount))))
                .andExpect(status().isOk());
    }

    // ---------- T-API-01 .. T-API-06 ----------

    /** T-API-01 (BANK-FR-01) */
    @Test
    void registerWorksAndDuplicateEmailIs409() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Demo User", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(content().string(not(containsString("password"))));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Demo User", "email", email, "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    /** T-API-02 (BANK-FR-02) */
    @Test
    void loginWithWrongPasswordOrUnknownEmailIs401WithTheSameMessage() throws Exception {
        String email = uniqueEmail();
        register(email);

        String wrongPassword = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", "Wrong#Pass9"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andReturn().getResponse().getContentAsString();
        String detail1 = JsonPath.read(wrongPassword, "$.detail");

        String unknownEmail = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", uniqueEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String detail2 = JsonPath.read(unknownEmail, "$.detail");

        assertEquals(detail1, detail2);
    }

    @Test
    void loginReturnsBearerTokenValidFor15Minutes() throws Exception {
        String email = uniqueEmail();
        register(email);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    /** T-API-03 (BANK-NFR-03) */
    @Test
    void noTokenOrGarbageTokenGives401InTheStandardShape() throws Exception {
        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/accounts"));

        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    /** T-API-04 (BANK-FR-09) */
    @Test
    void userCallingAdminEndpointGets403() throws Exception {
        String token = registerAndLogin();
        mvc.perform(get("/api/v1/admin/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    /** T-API-05 (BANK-FR-04) */
    @Test
    void readingAnotherUsersAccountGives404() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        long aliceAccount = openAccount(alice);

        mvc.perform(get("/api/v1/accounts/" + aliceAccount).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
        mvc.perform(post("/api/v1/accounts/" + aliceAccount + "/deposit").header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", "10.00"))))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/transactions").header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound());
    }

    /** T-API-06 (BANK-FR-10) */
    @Test
    void invalidBodyReturnsTheStandardErrorJsonWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/v1/auth/register"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(content().string(not(containsString("Exception"))));

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- end-to-end flow ----------

    @Test
    void depositWithdrawTransferAndHistoryEndToEnd() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        long aliceAccount = openAccount(alice);
        long bobAccount = openAccount(bob);
        String bobNumber = accountNumber(bob, bobAccount);

        deposit(alice, aliceAccount, "500.00");

        // transfer 250.00 alice -> bob (destination by account number)
        mvc.perform(post("/api/v1/transfers").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromAccountId", aliceAccount, "toAccountNumber", bobNumber,
                                "amount", "250.00", "description", "Rent share"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value("250.00"))
                .andExpect(jsonPath("$.toAccountNumber").value(bobNumber));

        mvc.perform(get("/api/v1/accounts/" + aliceAccount).header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.balance").value("250.00"));
        mvc.perform(get("/api/v1/accounts/" + bobAccount).header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.balance").value("250.00"));

        // history: newest first
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/transactions").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].entryType").value("TRANSFER_DEBIT"))
                .andExpect(jsonPath("$.content[1].entryType").value("DEPOSIT"))
                .andExpect(jsonPath("$.totalElements").value(2));

        // filter by type, empty filter values and pagination
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/transactions?type=DEPOSIT&from=&to=&page=0&size=1")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].entryType").value("DEPOSIT"));

        // bad filter value
        mvc.perform(get("/api/v1/accounts/" + aliceAccount + "/transactions?type=NOPE")
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void failureCasesUseTheStandardShape() throws Exception {
        String alice = registerAndLogin();
        String bob = registerAndLogin();
        long aliceAccount = openAccount(alice);
        long bobAccount = openAccount(bob);
        String bobNumber = accountNumber(bob, bobAccount);
        String aliceNumber = accountNumber(alice, aliceAccount);
        deposit(alice, aliceAccount, "100.00");

        // insufficient funds -> 422
        mvc.perform(post("/api/v1/accounts/" + aliceAccount + "/withdraw").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", "250.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        // three decimals -> 400
        mvc.perform(post("/api/v1/accounts/" + aliceAccount + "/deposit").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", "10.001"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // zero -> 400
        mvc.perform(post("/api/v1/accounts/" + aliceAccount + "/deposit").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", "0"))))
                .andExpect(status().isBadRequest());

        // transfer to own account -> 400
        mvc.perform(post("/api/v1/transfers").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromAccountId", aliceAccount, "toAccountNumber", aliceNumber, "amount", "1.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_ACCOUNT"));

        // unknown destination -> 404
        mvc.perform(post("/api/v1/transfers").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromAccountId", aliceAccount, "toAccountNumber", "999999999999", "amount", "1.00"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));

        // source I do not own -> 404
        mvc.perform(post("/api/v1/transfers").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromAccountId", bobAccount, "toAccountNumber", aliceNumber, "amount", "1.00"))))
                .andExpect(status().isNotFound());

        // bad destination format -> 400
        mvc.perform(post("/api/v1/transfers").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromAccountId", aliceAccount, "toAccountNumber", "123", "amount", "1.00"))))
                .andExpect(status().isBadRequest());

        // balances untouched by all of the above
        mvc.perform(get("/api/v1/accounts/" + aliceAccount).header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.balance").value("100.00"));
        mvc.perform(get("/api/v1/accounts/" + bobAccount).header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.balance").value("0.00"));
        // quiet the unused-variable warning for bobNumber
        assertEquals(12, bobNumber.length());
    }

    /** BANK-FR-09 through HTTP: admin freezes, money operations return 409, admin unfreezes. */
    @Test
    void adminCanFreezeAndUnfreezeAnAccount() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.com";
        User admin = users.save(new User(email, passwordEncoder.encode(PASSWORD), "Admin", Role.ADMIN));
        String adminToken = login(admin.getEmail(), PASSWORD);

        String alice = registerAndLogin();
        long aliceAccount = openAccount(alice);
        deposit(alice, aliceAccount, "100.00");

        mvc.perform(patch("/api/v1/admin/accounts/" + aliceAccount + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "FROZEN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        mvc.perform(post("/api/v1/accounts/" + aliceAccount + "/deposit").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", "5.00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_FROZEN"));
        mvc.perform(post("/api/v1/accounts/" + aliceAccount + "/withdraw").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", "5.00"))))
                .andExpect(status().isConflict());

        // a USER cannot change status
        mvc.perform(patch("/api/v1/admin/accounts/" + aliceAccount + "/status")
                        .header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isForbidden());

        // CLOSED is not allowed through the API
        mvc.perform(patch("/api/v1/admin/accounts/" + aliceAccount + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "CLOSED"))))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/admin/accounts/" + aliceAccount + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk());
        deposit(alice, aliceAccount, "5.00");

        mvc.perform(get("/api/v1/admin/accounts?page=0&size=5").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void unknownPathIs404InTheStandardShapeWhenLoggedIn() throws Exception {
        String token = registerAndLogin();
        mvc.perform(get("/api/v1/does-not-exist").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/v1/does-not-exist"));
    }
}
