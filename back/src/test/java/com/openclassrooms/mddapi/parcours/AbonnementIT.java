package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « S'abonner à un thème via la page des thèmes ».
 * Route : {@code POST /api/user/subscriptions/{topicId}}.
 */
class AbonnementIT extends ActionIT {

    /** Act commun : abonne l'utilisateur connecté au thème désigné dans l'URL. */
    private ResultActions sAbonner(String idTheme) throws Exception {
        return mockMvc.perform(post("/api/user/subscriptions/" + idTheme));
    }

    @Test
    void abonnement_themeExistant_retourne201EtEnregistreLAbonnement() throws Exception {
        // Arrange
        User compte = creerUtilisateur("abo-ok");
        connecterEnTantQue(compte);
        Long idJava = idDuTheme("Java");

        // Act
        ResultActions reponse = sAbonner(idJava.toString());

        // Assert
        reponse.andExpect(status().isCreated());
        viderLeCache();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(compte.getId(), idJava)).isTrue();
    }

    @Test
    void abonnement_dejaAbonne_retourne409() throws Exception {
        // Arrange
        User compte = creerUtilisateur("abo-double");
        Long idJava = idDuTheme("Java");
        abonner(compte, idJava);
        connecterEnTantQue(compte);

        // Act
        ResultActions reponse = sAbonner(idJava.toString());

        // Assert
        reponse.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vous êtes déjà abonné à ce topic"));
    }

    @Test
    void abonnement_themeInexistant_retourne404() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("abo-inexistant"));

        // Act
        ResultActions reponse = sAbonner("999999");

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce topic n'existe pas"));
    }

    @Test
    void abonnement_identifiantNonNumerique_retourne400() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("abo-abc"));

        // Act
        ResultActions reponse = sAbonner("abc");

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le paramètre fourni est invalide"));
    }
}
