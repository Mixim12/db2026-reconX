package com.dbtraining.reconx.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // 1. Unauthenticated requests
    @Test
    public void givenNoAuth_whenGetTrades_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/trades"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 401 && status != 403) {
                        throw new AssertionError("Expected 401 or 403 but got " + status);
                    }
                });
    }

    @Test
    public void givenNoAuth_whenPostLogin_thenPermitted() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@db.com\",\"password\":\"test\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("Expected permitted status but got " + status);
                    }
                });
    }

    @Test
    public void givenNoAuth_whenGetHealth_thenPermitted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Expected permitted status but got " + status);
                    }
                });
    }

    // 2. VIEWER Role Tests
    @Test
    @WithMockUser(roles = "VIEWER")
    public void givenViewer_whenGetTrades_thenOk() throws Exception {
        mockMvc.perform(get("/v1/trades"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Expected permitted status but got " + status);
                    }
                });
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    public void givenViewer_whenPostTrades_thenForbidden() throws Exception {
        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // 3. TRADER Role Tests
    @Test
    @WithMockUser(roles = "TRADER")
    public void givenTrader_whenPostTrades_thenAllowed() throws Exception {
        mockMvc.perform(post("/v1/trades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Expected permitted status but got " + status);
                    }
                });
    }

    @Test
    @WithMockUser(roles = "TRADER")
    public void givenTrader_whenDeleteTrades_thenForbidden() throws Exception {
        mockMvc.perform(delete("/v1/trades/123"))
                .andExpect(status().isForbidden());
    }

    // 4. ADMIN Role Tests
    @Test
    @WithMockUser(roles = "ADMIN")
    public void givenAdmin_whenDeleteTrades_thenAllowed() throws Exception {
        mockMvc.perform(delete("/v1/trades/123"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Expected permitted status but got " + status);
                    }
                });
    }

    // 5. RECON_ANALYST Role Tests
    @Test
    @WithMockUser(roles = "RECON_ANALYST")
    public void givenReconAnalyst_whenPostRecon_thenAllowed() throws Exception {
        mockMvc.perform(post("/v1/recon/run"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Expected permitted status but got " + status);
                    }
                });
    }

    @Test
    @WithMockUser(roles = "TRADER")
    public void givenTrader_whenPostRecon_thenForbidden() throws Exception {
        mockMvc.perform(post("/v1/recon/run"))
                .andExpect(status().isForbidden());
    }
}
