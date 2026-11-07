package com.banksim.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banksim.AbstractIntegrationTest;
import com.banksim.user.Role;
import com.banksim.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class LoginThrottleIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    private void attempt(String email, String password, int expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void sixthAttemptIsBlockedEvenWithTheCorrectPassword() throws Exception {
        User user = newUser(Role.USER);
        for (int i = 0; i < 5; i++) {
            attempt(user.getEmail(), "wrong-password", 401);
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"Passw0rd!x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        User user = newUser(Role.USER);
        for (int i = 0; i < 4; i++) {
            attempt(user.getEmail(), "wrong-password", 401);
        }
        attempt(user.getEmail(), "Passw0rd!x", 200);
        for (int i = 0; i < 4; i++) {
            attempt(user.getEmail(), "wrong-password", 401);
        }
        attempt(user.getEmail(), "Passw0rd!x", 200);
    }
}
