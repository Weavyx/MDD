package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « S'abonner à un thème via la page des thèmes ».
 * Route : {@code POST /api/user/subscriptions/{topicId}}.
 */
class AbonnementIT extends ParcoursIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /** Act commun : abonne le porteur de {@code jeton} au thème désigné dans l'URL. */
    private ResultActions sAbonner(String jeton, String idTheme) throws Exception {
        return mockMvc.perform(post("/api/user/subscriptions/" + idTheme)
                .header("Authorization", "Bearer " + jeton));
    }

    @Test
    void abonnement_themeExistant_retourne201EtEnregistreLAbonnement() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("abo-ok");
        Long idJava = idDuTheme("Java");

        // Act
        ResultActions reponse = sAbonner(jeton, idJava.toString());

        // Assert
        reponse.andExpect(status().isCreated());
        viderLeCache();
        Long idCompte = userRepository.findByEmailOrUsername("abo-ok").orElseThrow().getId();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(idCompte, idJava)).isTrue();
    }

    @Test
    void abonnement_dejaAbonne_retourne409() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("abo-double");
        Long idJava = idDuTheme("Java");
        abonner(jeton, idJava);

        // Act
        ResultActions reponse = sAbonner(jeton, idJava.toString());

        // Assert
        reponse.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vous êtes déjà abonné à ce topic"));
    }

    @Test
    void abonnement_themeInexistant_retourne404() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("abo-inexistant");

        // Act
        ResultActions reponse = sAbonner(jeton, "999999");

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce topic n'existe pas"));
    }

    @Test
    void abonnement_identifiantNonNumerique_retourne400() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("abo-abc");

        // Act
        ResultActions reponse = sAbonner(jeton, "abc");

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le paramètre fourni est invalide"));
    }
}
