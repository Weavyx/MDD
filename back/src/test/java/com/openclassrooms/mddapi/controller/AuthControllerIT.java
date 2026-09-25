package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.AuthResponse;
import com.openclassrooms.mddapi.dto.LoginRequest;
import com.openclassrooms.mddapi.exception.EmailAlreadyUsedException;
import com.openclassrooms.mddapi.exception.GlobalExceptionHandler;
import com.openclassrooms.mddapi.exception.UsernameAlreadyUsedException;
import com.openclassrooms.mddapi.security.SecurityConfig;
import com.openclassrooms.mddapi.service.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void register_donneesValides_retourne201EtLeToken() throws Exception {
        when(authService.register(any())).thenReturn(new AuthResponse("jwt-token"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"Password1!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                // Séparation DTO/entité : aucun secret ne doit fuiter dans la réponse.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_emailDejaUtilise_retourne409() throws Exception {
        when(authService.register(any())).thenThrow(new EmailAlreadyUsedException("Cet email est déjà utilisé"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"Password1!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Cet email est déjà utilisé"));
    }

    @Test
    void register_usernameDejaUtilise_retourne409() throws Exception {
        when(authService.register(any())).thenThrow(new UsernameAlreadyUsedException("Ce nom d'utilisateur est déjà utilisé"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"Password1!\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ce nom d'utilisateur est déjà utilisé"));
    }

    @Test
    void register_emailInvalide_retourne400EtServiceJamaisAppele() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\",\"username\":\"alice\",\"password\":\"Password1!\"}"))
                .andExpect(status().isBadRequest())
                // Contrat d'erreur de validation : fieldErrors indexé par nom de champ du DTO,
                // valeur = message déclaré dans l'annotation (affichage par champ côté front).
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Requête invalide"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.fieldErrors.email").value("L'adresse e-mail doit être valide"));

        verify(authService, never()).register(any());
    }

    @Test
    void register_motDePasseNonConformeAuPattern_retourne400EtServiceJamaisAppele() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"motdepasse1!\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_motDePasseDepassant72Octets_retourne400EtServiceJamaisAppele() throws Exception {
        // 73 caractères ASCII (73 octets), conformes au pattern : seule la borne
        // @MaxUtf8Bytes(72) doit échouer (BCrypt refuse au-delà de 72 octets).
        String password = "Password1!" + "a".repeat(63);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.password").value("Le mot de passe ne doit pas dépasser 72 octets (un caractère accentué en compte 2)"));

        verify(authService, never()).register(any());
    }

    @Test
    void register_motDePasseAccentueDe42CaracteresEt74Octets_retourne400EtServiceJamaisAppele() throws Exception {
        // 42 caractères (sous l'ancienne borne @Size(max = 72) en caractères) mais 74 octets
        // UTF-8 : c'est le cas qui atteignait BCrypt et finissait en 500.
        String password = "Password1!" + "\u00e9".repeat(32);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Le mot de passe ne doit pas dépasser 72 octets (un caractère accentué en compte 2)"));

        verify(authService, never()).register(any());
    }

    @Test
    void register_usernameTropCourt_retourne400EtServiceJamaisAppele() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"ab\",\"password\":\"Password1!\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).register(any());
    }

    @Test
    void register_motDePasseAbsent_retourne400EtServiceJamaisAppele() throws Exception {
        // @Size et @Pattern acceptent null par conception (Bean Validation) : sans @NotBlank,
        // un corps sans clé "password" franchissait la validation et atteignait
        // passwordEncoder.encode(null) dans le service (500 hors ErrorResponse).
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value("Le mot de passe est obligatoire"));

        verify(authService, never()).register(any());
    }

    @Test
    void login_identifiantsValides_retourne200EtLeToken() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice@mail.com\",\"password\":\"Password1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                // Séparation DTO/entité : aucun secret ne doit fuiter dans la réponse.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void login_parUsernameOuEmail_transmetLIdentifiantTelQuelAuService() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-token"));
        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice\",\"password\":\"Password1!\"}"))
                .andExpect(status().isOk());

        verify(authService).login(captor.capture());
        assertThat(captor.getValue().getIdentifier()).isEqualTo("alice");
    }

    @Test
    void login_identifiantsInvalides_retourne401() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        // Forme figée : BadCredentialsException n'est pas gérée par GlobalExceptionHandler,
        // c'est l'entry point Bearer de Spring Security qui répond — 401, corps vide,
        // en-tête WWW-Authenticate "Bearer ...". Pas d'ErrorResponse JSON ici.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice@mail.com\",\"password\":\"MauvaisMdp1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().string(""));
    }

    @Test
    void register_corpsJsonMalforme_retourne400() throws Exception {
        // Forme figée : HttpMessageNotReadableException n'a pas de handler dédié, Spring MVC
        // répond par défaut (sendError 400) — corps vide sous MockMvc, pas d'ErrorResponse.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));

        verify(authService, never()).register(any());
    }

    @Test
    void login_identifiantAbsent_retourne400EtServiceJamaisAppele() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"Password1!\"}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any());
    }
}
