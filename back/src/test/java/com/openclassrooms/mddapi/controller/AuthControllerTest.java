package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.AuthenticatedUserResponse;
import com.openclassrooms.mddapi.exception.GlobalExceptionHandler;
import com.openclassrooms.mddapi.exception.UserNotFoundException;
import com.openclassrooms.mddapi.security.SecurityConfig;
import com.openclassrooms.mddapi.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void me_avecJwtValide_retourne200EtIdPlusUsername() throws Exception {
        when(authService.getCurrentUser(1L)).thenReturn(new AuthenticatedUserResponse(1L, "alice"));

        mockMvc.perform(get("/api/auth/me").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void me_utiliseLeSubDuJwtCommeIdUtilisateur() throws Exception {
        when(authService.getCurrentUser(42L)).thenReturn(new AuthenticatedUserResponse(42L, "bob"));

        mockMvc.perform(get("/api/auth/me").with(jwt().jwt(jwt -> jwt.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42));

        verify(authService).getCurrentUser(42L);
    }

    @Test
    void me_userNotFoundException_retourne404() throws Exception {
        when(authService.getCurrentUser(99L)).thenThrow(new UserNotFoundException("Cet utilisateur n'existe pas"));

        mockMvc.perform(get("/api/auth/me").with(jwt().jwt(jwt -> jwt.subject("99"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Cet utilisateur n'existe pas"));
    }

    @Test
    void me_sansJwt_retourne401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        verify(authService, never()).getCurrentUser(org.mockito.ArgumentMatchers.anyLong());
    }
}
