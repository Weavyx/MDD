package com.openclassrooms.mddapi.parcours;

import com.jayway.jsonpath.JsonPath;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « S'inscrire grâce à un e-mail, un mot de passe et un nom
 * d'utilisateur ». Route : {@code POST /api/auth/register}.
 */
class InscriptionIT extends ParcoursIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    /** Act commun : envoie {@code corps} à la route d'inscription. */
    private ResultActions envoyerInscription(String corps) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    @Test
    void inscription_donneesValides_retourne201EtCreeLeCompte() throws Exception {
        // Arrange
        String corps = corpsInscription("inscription-ok", "inscription-ok@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        viderLeCache();
        User compte = userRepository.findByEmailOrUsername("inscription-ok@mail.com").orElseThrow();
        assertThat(compte.getUsername()).isEqualTo("inscription-ok");
        assertThat(compte.getPasswordHash()).isNotEqualTo(MOT_DE_PASSE);
        assertThat(passwordEncoder.matches(MOT_DE_PASSE, compte.getPasswordHash())).isTrue();
        String jeton = JsonPath.read(reponse.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        assertThat(jwtDecoder.decode(jeton).getSubject()).isEqualTo(String.valueOf(compte.getId()));
    }

    @Test
    void inscription_emailDejaUtilise_retourne409() throws Exception {
        // Arrange
        inscrire("deja-inscrit");
        String corps = corpsInscription("nouveau-nom", "deja-inscrit@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cet email est déjà utilisé"));
        viderLeCache();
        assertThat(userRepository.findByEmailOrUsername("nouveau-nom")).isEmpty();
    }

    @Test
    void inscription_usernameDejaUtilise_retourne409() throws Exception {
        // Arrange
        inscrire("deja-inscrit");
        String corps = corpsInscription("deja-inscrit", "nouvel-email@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce nom d'utilisateur est déjà utilisé"));
        viderLeCache();
        assertThat(userRepository.findByEmailOrUsername("nouvel-email@mail.com")).isEmpty();
    }

    @Test
    void inscription_emailInvalide_retourne400() throws Exception {
        // Arrange
        String corps = corpsInscription("email-invalide", "pas-un-email", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Requête invalide"))
                .andExpect(jsonPath("$.fieldErrors.email").value("L'adresse e-mail doit être valide"));
    }

    static Stream<String> usernamesInvalides() {
        return Stream.of("ab", "a".repeat(51), "a@b.fr");
    }

    @ParameterizedTest(name = "nom refusé : {0}")
    @MethodSource("usernamesInvalides")
    void inscription_usernameInvalide_retourne400(String username) throws Exception {
        // Arrange
        String corps = corpsInscription(username, "nom-invalide@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void inscription_usernameAvecPointTiretEtTiretBas_retourne201() throws Exception {
        // Arrange
        String corps = corpsInscription("demo_user-1.x", "demo-user@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isCreated());
    }

    @ParameterizedTest(name = "mot de passe refusé : {0}")
    @ValueSource(strings = {"Password!", "PASSWORD1!", "password1!", "Password1", "Pa1!xyz"})
    void inscription_motDePasseNonConforme_retourne400(String motDePasse) throws Exception {
        // Arrange
        String corps = corpsInscription("mdp-faible", "mdp-faible@mail.com", motDePasse);

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void inscription_motDePasseDe74Octets_retourne400() throws Exception {
        // Arrange : 10 caractères ASCII + 32 « é » de 2 octets = 74 octets, mais 42 caractères
        String corps = corpsInscription("mdp-long", "mdp-long@mail.com", MOT_DE_PASSE + "é".repeat(32));

        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value(containsString("72 octets")));
    }

    @ParameterizedTest(name = "champ absent : {0}")
    @CsvSource(delimiter = '|', textBlock = """
            email    | {"username":"sans-email","password":"Password1!"}
            username | {"email":"sans-nom@mail.com","password":"Password1!"}
            password | {"username":"sans-mdp","email":"sans-mdp@mail.com"}
            """)
    void inscription_champObligatoireAbsent_retourne400(String champ, String corps) throws Exception {
        // Arrange : corps fourni par @CsvSource, un champ manquant par cas
        // Act
        ResultActions reponse = envoyerInscription(corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.%s", champ).exists());
    }
}