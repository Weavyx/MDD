package com.openclassrooms.mddapi.parcours;

import com.jayway.jsonpath.JsonPath;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Se connecter à partir d'un e-mail ou d'un nom d'utilisateur
 * et d'un mot de passe ». Route : {@code POST /api/auth/login}.
 * <p>
 * La persistance de la connexion entre les sessions relève du front (jeton en
 * {@code localStorage}) : elle est vérifiée par les tests e2e, pas ici.
 */
class ConnexionIT extends ChaineDeSecuriteIT {

    @Autowired
    private JwtService jwtService;

    /** Act commun : envoie {@code corps} à la route de connexion. */
    private ResultActions envoyerConnexion(String corps) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    /** Corps JSON de {@code POST /api/auth/login}. */
    private static String corpsConnexion(String identifiant, String motDePasse) {
        return "{\"identifier\":\"" + identifiant + "\",\"password\":\"" + motDePasse + "\"}";
    }

    /** Assert commun : le jeton renvoyé porte l'id de {@code compte}. */
    private void verifierJetonDuCompte(ResultActions reponse, User compte) throws Exception {
        String jeton = JsonPath.read(reponse.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        assertThat(jwtService.extractUserId(jeton)).isEqualTo(compte.getId());
    }

    @Test
    void connexion_parEmail_retourne200EtUnJetonDuCompte() throws Exception {
        // Arrange
        User compte = creerCompte("connexion-email", MOT_DE_PASSE);
        String corps = corpsConnexion("connexion-email@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        verifierJetonDuCompte(reponse, compte);
    }

    @Test
    void connexion_parNomDUtilisateur_retourne200EtUnJetonDuCompte() throws Exception {
        // Arrange
        User compte = creerCompte("connexion-nom", MOT_DE_PASSE);
        String corps = corpsConnexion("connexion-nom", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
        verifierJetonDuCompte(reponse, compte);
    }

    @Test
    void connexion_motDePasseIncorrect_retourne401SansCorps() throws Exception {
        // Arrange
        creerCompte("connexion-mdp-faux", MOT_DE_PASSE);
        String corps = corpsConnexion("connexion-mdp-faux@mail.com", "Mauvais1!");

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().string(""));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void connexion_motDePasseIncorrect_journaliseLEchecAvecLIdentifiantInterne(CapturedOutput output) throws Exception {
        // Arrange
        User compte = creerCompte("connexion-journal", MOT_DE_PASSE);
        String corps = corpsConnexion("connexion-journal@mail.com", "Mauvais1!");

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isUnauthorized());
        assertThat(output.getAll().lines().filter(ligne -> ligne.contains("Échec de connexion : utilisateur " + compte.getId())))
                .singleElement().asString().contains(" WARN ");
        assertThat(output.getAll()).doesNotContain("connexion-journal", "connexion-journal@mail.com");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void connexion_identifiantInconnu_journaliseUnEchecSurUnCompteInconnu(CapturedOutput output) throws Exception {
        // Arrange : aucun compte ne porte cet identifiant
        String corps = corpsConnexion("journal-inconnu@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isUnauthorized());
        assertThat(output.getAll().lines().filter(ligne -> ligne.contains("Échec de connexion : compte inconnu")))
                .singleElement().asString().contains(" WARN ");
        assertThat(output.getAll()).doesNotContain("journal-inconnu");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void connexion_parEmail_journaliseLaConnexionAvecLIdentifiantInterne(CapturedOutput output) throws Exception {
        // Arrange
        User compte = creerCompte("connexion-reussie", MOT_DE_PASSE);
        String corps = corpsConnexion("connexion-reussie@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isOk());
        assertThat(output.getAll().lines().filter(ligne -> ligne.contains("Connexion réussie : utilisateur " + compte.getId())))
                .singleElement().asString().contains(" INFO ");
        assertThat(output.getAll()).doesNotContain("connexion-reussie", "connexion-reussie@mail.com");
    }

    @Test
    void connexion_identifiantInconnu_retourne401SansOuvrirUnAutreCompte() throws Exception {
        // Arrange : un compte existe avec ce mot de passe, mais l'identifiant envoyé n'est pas le sien
        creerCompte("connexion-existant", MOT_DE_PASSE);
        String corps = corpsConnexion("inconnu@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    @Test
    void connexion_motDePasseDePlusDe72Octets_retourne401() throws Exception {
        // Arrange : la connexion n'a pas de borne en octets ; au-delà de 72, aucun hash BCrypt ne correspond
        creerCompte("connexion-long", MOT_DE_PASSE);
        String corps = corpsConnexion("connexion-long@mail.com", MOT_DE_PASSE + "é".repeat(32));

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isUnauthorized());
    }

    @Test
    void connexion_jetonRenvoye_valable1440MinutesApresSonEmission() throws Exception {
        // Arrange
        creerCompte("connexion-duree", MOT_DE_PASSE);
        String corps = corpsConnexion("connexion-duree@mail.com", MOT_DE_PASSE);

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert : charge utile lue sans vérifier la signature, comme le front lit exp
        reponse.andExpect(status().isOk());
        String jeton = JsonPath.read(reponse.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        String chargeUtile = new String(Base64.getUrlDecoder().decode(jeton.split("\\.")[1]), StandardCharsets.UTF_8);
        long emisLe = ((Number) JsonPath.read(chargeUtile, "$.iat")).longValue();
        long expireLe = ((Number) JsonPath.read(chargeUtile, "$.exp")).longValue();
        assertThat(expireLe - emisLe).isEqualTo(Duration.ofMinutes(1440).toSeconds());
    }

    @Test
    void connexion_motDePasseAccentueSaisiSousUneAutreFormeUnicode_retourne200() throws Exception {
        // Arrange : compte créé avec « é » en un seul caractère (NFC), connexion avec « e » suivi de l'accent combinant (NFD)
        String motDePasseNfc = Normalizer.normalize("Mot2passé!", Normalizer.Form.NFC);
        String motDePasseNfd = Normalizer.normalize(motDePasseNfc, Normalizer.Form.NFD);
        assertThat(motDePasseNfd).isNotEqualTo(motDePasseNfc);
        User compte = creerCompte("connexion-nfc", motDePasseNfc);
        String corps = corpsConnexion("connexion-nfc@mail.com", motDePasseNfd);

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isOk());
        verifierJetonDuCompte(reponse, compte);
    }

    @ParameterizedTest(name = "champ absent : {0}")
    @CsvSource(delimiter = '|', textBlock = """
            identifier | {"password":"Password1!"}
            password   | {"identifier":"absent@mail.com"}
            """)
    void connexion_champObligatoireAbsent_retourne400(String champ, String corps) throws Exception {
        // Arrange : corps fourni par @CsvSource, un champ manquant par cas

        // Act
        ResultActions reponse = envoyerConnexion(corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.%s", champ).exists());
    }
}
