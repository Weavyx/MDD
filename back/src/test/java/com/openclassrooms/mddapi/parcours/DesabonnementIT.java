package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Se désabonner via la page de profil ».
 * Route : {@code DELETE /api/user/subscriptions/{topicId}} (idempotente : 204 même sans abonnement).
 */
class DesabonnementIT extends ActionIT {

    /** Act commun : désabonne l'utilisateur connecté du thème {@code idTheme}. */
    private ResultActions seDesabonner(Long idTheme) throws Exception {
        return mockMvc.perform(delete("/api/user/subscriptions/{id}", idTheme));
    }

    @Test
    void desabonnement_themeSuivi_retourne204EtSupprimeLAbonnement() throws Exception {
        // Arrange
        User compte = creerUtilisateur("desabo-ok");
        Long idJava = idDuTheme("Java");
        abonner(compte, idJava);
        connecterEnTantQue(compte);

        // Act
        ResultActions reponse = seDesabonner(idJava);

        // Assert
        reponse.andExpect(status().isNoContent());
        viderLeCache();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(compte.getId(), idJava)).isFalse();
    }

    @Test
    void desabonnement_themeNonSuivi_retourne204() throws Exception {
        // Arrange : aucun abonnement préalable
        connecterEnTantQue(creerUtilisateur("desabo-rien"));

        // Act
        ResultActions reponse = seDesabonner(idDuTheme("Java"));

        // Assert
        reponse.andExpect(status().isNoContent());
    }

    @Test
    void desabonnement_nAffectePasLesAutresComptes() throws Exception {
        // Arrange : deux comptes suivent Java
        User autre = creerUtilisateur("desabo-autre");
        Long idJava = idDuTheme("Java");
        abonner(autre, idJava);
        User compte = creerUtilisateur("desabo-moi");
        abonner(compte, idJava);
        connecterEnTantQue(compte);

        // Act
        ResultActions reponse = seDesabonner(idJava);

        // Assert
        reponse.andExpect(status().isNoContent());
        viderLeCache();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(compte.getId(), idJava)).isFalse();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(autre.getId(), idJava)).isTrue();
    }

    @Test
    void desabonnement_themeInexistant_retourne404() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("desabo-inexistant"));

        // Act
        ResultActions reponse = seDesabonner(999999L);

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce topic n'existe pas"));
    }
}
