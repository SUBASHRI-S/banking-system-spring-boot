package com.banksim;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** The web app files are public (a visitor has no token yet), while the API stays protected. */
class StaticAppIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void webAppFilesAreServedWithoutAToken() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/assets/app.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/app.css")).andExpect(status().isOk());
    }

    @Test
    void theApiStillNeedsAToken() throws Exception {
        mvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
    }
}
