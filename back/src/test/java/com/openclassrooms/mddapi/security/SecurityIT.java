package com.openclassrooms.mddapi.security;

import com.openclassrooms.mddapi.AbstractContainerIT;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import com.openclassrooms.mddapi.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chaîne complète (vraie base, vrais {@code PasswordEncoder}, {@code AuthenticationManager}
 * et {@code JwtDecoder}) pour les cas que les tests {@code @WebMvcTest} ne voient pas.
 * {@code @Transactional} : MockMvc s'exécute dans le thread du test, chaque test est annulé.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIT extends AbstractContainerIT {

    private static final String EMAIL = "security-it@mail.com";
    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User user;

    @BeforeEach
    void createUser() {
        user = new User();
        user.setEmail(EMAIL);
        user.setUsername("security-it");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user = userRepository.save(user);
    }

    @Test
    void login_utilisateurExistantBonMotDePasse_retourne200() throws Exception {
        // Témoin : le compte existe bien, le 401 du test suivant vient du mot de passe.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void login_utilisateurExistantMotDePasseDe74Octets_retourne401() throws Exception {
        // 42 caractères, 74 octets UTF-8 : BCrypt refuse de l'encoder, mais sa vérification
        // renvoie simplement false. Aucune borne en octets n'est donc nécessaire sur
        // LoginRequest : le login échoue en 401 ordinaire, pas en 500.
        String password = "Password1!" + "é".repeat(32);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_motDePasseAbsent_retourne200EtHashInchange() throws Exception {
        String hashAvant = user.getPasswordHash();
        String token = jwtService.generateToken(user.getId().toString());

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"username\":\"security-it-2\"}"))
                .andExpect(status().isOk());

        User apres = userRepository.findById(user.getId()).orElseThrow();
        assertThat(apres.getUsername()).isEqualTo("security-it-2");
        assertThat(apres.getPasswordHash()).isEqualTo(hashAvant);
    }
}
