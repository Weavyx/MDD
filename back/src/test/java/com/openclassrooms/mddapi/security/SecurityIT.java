package com.openclassrooms.mddapi.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
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
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

    /** Encodeur de l'application : même clé que le {@code JwtDecoder} testé. */
    @Autowired
    private JwtEncoder jwtEncoder;

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

    @Test
    void getProfile_jetonValide_retourne200() throws Exception {
        String token = jwtService.generateToken(user.getId().toString());

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void getProfile_jetonSigneAvecUneAutreCle_retourne401() throws Exception {
        byte[] autreCle = new byte[32];
        new SecureRandom().nextBytes(autreCle);
        JwtEncoder autreEncodeur = new NimbusJwtEncoder(new ImmutableSecret<>(autreCle));
        Instant now = Instant.now();
        String token = encode(autreEncodeur, now, now.plus(1, ChronoUnit.HOURS));

        // "invalid_token" : le jeton a été lu puis refusé, ce n'est pas le 401 "jeton absent".
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    @Test
    void getProfile_jetonExpire_retourne401() throws Exception {
        // Bonne clé, exp une heure dans le passé : au-delà des 60 s de tolérance
        // du JwtTimestampValidator par défaut.
        Instant now = Instant.now();
        String token = encode(jwtEncoder, now.minus(2, ChronoUnit.HOURS), now.minus(1, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    private String encode(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mdd-api")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
