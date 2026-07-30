package com.dbtraining.reconx.controller;

import com.dbtraining.reconx.exception.GlobalExceptionHandler;
import com.dbtraining.reconx.repository.AppUserRepository;
import com.dbtraining.reconx.repository.entity.AppUser;
import com.dbtraining.reconx.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TICKET-ADV072 — POST /auth/login issues a JWT, and refuses to say why it didn't.
 *
 * A standalone MockMvc wired with the real GlobalExceptionHandler: the status
 * code for bad credentials is part of the contract, so the advice has to be in
 * the picture.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    private static final String VALID_BODY =
            "{\"email\":\"trader@db.com\",\"password\":\"trader123\"}";

    @Mock
    private AppUserRepository users;

    @Mock
    private PasswordEncoder encoder;

    @Mock
    private JwtTokenProvider jwt;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(users, encoder, jwt))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AppUser trader(boolean enabled) {
        AppUser user = org.mockito.Mockito.mock(AppUser.class);
        when(user.getEmail()).thenReturn("trader@db.com");
        when(user.getPasswordHash()).thenReturn("$2y$10$hash");
        when(user.getRole()).thenReturn("TRADER");
        when(user.getEnabled()).thenReturn(enabled);
        return user;
    }

    @Test
    @DisplayName("valid credentials return 200 with the Bearer token envelope")
    void validLoginReturnsToken() throws Exception {
        AppUser trader = trader(true);
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.of(trader));
        when(encoder.matches("trader123", "$2y$10$hash")).thenReturn(true);
        when(jwt.generate("trader@db.com", "TRADER")).thenReturn("header.payload.signature");
        when(jwt.expirationSeconds()).thenReturn(3600L);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("header.payload.signature"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.role").value("TRADER"));
    }

    @Test
    @DisplayName("the token is signed for the authenticated user's own role")
    void tokenIsIssuedForTheAuthenticatedUser() throws Exception {
        AppUser trader = trader(true);
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.of(trader));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwt.generate(anyString(), anyString())).thenReturn("header.payload.signature");

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());

        verify(jwt).generate("trader@db.com", "TRADER");
    }

    @Test
    @DisplayName("an unknown email returns 401 and never mints a token")
    void unknownEmailIsUnauthorized() throws Exception {
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());

        verifyNoInteractions(jwt);
    }

    @Test
    @DisplayName("an unknown email still pays for a hash comparison — no timing oracle")
    void unknownEmailStillHashes() throws Exception {
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verify(encoder).matches(eq("trader123"), any());
    }

    @Test
    @DisplayName("a disabled account still pays for a hash comparison — no timing oracle")
    void disabledAccountStillHashes() throws Exception {
        AppUser disabled = trader(false);
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.of(disabled));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verify(encoder).matches("trader123", "$2y$10$hash");
    }

    @Test
    @DisplayName("a wrong password returns 401 and never mints a token")
    void wrongPasswordIsUnauthorized() throws Exception {
        AppUser trader = trader(true);
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.of(trader));
        when(encoder.matches(eq("trader123"), anyString())).thenReturn(false);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());

        verifyNoInteractions(jwt);
    }

    @Test
    @DisplayName("a disabled account returns 401 even with the right password")
    void disabledAccountIsUnauthorized() throws Exception {
        AppUser disabled = trader(false);
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.of(disabled));
        when(encoder.matches(anyString(), anyString())).thenReturn(true);

        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(jwt);
    }

    @Test
    @DisplayName("an unknown email and a wrong password are indistinguishable to the caller")
    void failuresAreIndistinguishable() throws Exception {
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.empty());
        String unknownEmail = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andReturn().getResponse().getContentAsString();

        AppUser trader = trader(true);
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.of(trader));
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        String wrongPassword = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownEmail).isEqualTo(wrongPassword);
        assertThat(unknownEmail).doesNotContain("trader@db.com");
    }

    @Test
    @DisplayName("a blank password is rejected as a validation error, not an auth attempt")
    void blankPasswordIsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"trader@db.com\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(users, jwt);
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("a malformed email is rejected as a validation error")
    void malformedEmailIsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"trader123\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(users, jwt);
        verify(encoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("the raw password is never echoed back to the caller")
    void neverEchoesThePassword() throws Exception {
        AppUser trader = trader(true);
        when(users.findByEmail("trader@db.com")).thenReturn(Optional.of(trader));
        when(encoder.matches(any(), any())).thenReturn(false);

        String body = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("trader123");
    }
}
