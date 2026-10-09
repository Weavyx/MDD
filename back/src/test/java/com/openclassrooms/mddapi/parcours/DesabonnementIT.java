package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Se désabonner via la page de profil ».
 * Route : {@code DELETE /api/user/subscriptions/{topicId}} (idempotente : 204 même sans abonnement).
 */
class DesabonnementIT extends ParcoursIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    /** Act commun : désabonne le porteur de {@code jeton} du thème {@code idTheme}. */
    private ResultActions seDesabonner(String jeton, Long idTheme) throws Exception {
        return mockMvc.perform(delete("/api/user/subscriptions/{id}", idTheme)
                .header("Authorization", "Bearer " + jeton));
    }

    /** Relecture en base de l'id d'un compte. */
    private Long idCompte(String username) {
        return userRepository.findByEmailOrUsername(username).orElseThrow().getId();
    }

    @Test
    void desabonnement_themeSuivi_retourne204EtSupprimeLAbonnement() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("desabo-ok");
        Long idJava = idDuTheme("Java");
        abonner(jeton, idJava);

        // Act
        ResultActions reponse = seDesabonner(jeton, idJava);

        // Assert
        reponse.andExpect(status().isNoContent());
        viderLeCache();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(idCompte("desabo-ok"), idJava)).isFalse();
    }

    @Test
    void desabonnement_themeNonSuivi_retourne204() throws Exception {
        // Arrange : aucun abonnement préalable
        String jeton = inscrireEtConnecter("desabo-rien");

        // Act
        ResultActions reponse = seDesabonner(jeton, idDuTheme("Java"));

        // Assert
        reponse.andExpect(status().isNoContent());
    }

    @Test
    void desabonnement_nAffectePasLesAutresComptes() throws Exception {
        // Arrange : deux comptes suivent Java
        String jetonAutre = inscrireEtConnecter("desabo-autre");
        Long idJava = idDuTheme("Java");
        abonner(jetonAutre, idJava);
        String jeton = inscrireEtConnecter("desabo-moi");
        abonner(jeton, idJava);

        // Act
        ResultActions reponse = seDesabonner(jeton, idJava);

        // Assert
        reponse.andExpect(status().isNoContent());
        viderLeCache();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(idCompte("desabo-moi"), idJava)).isFalse();
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(idCompte("desabo-autre"), idJava)).isTrue();
    }

    @Test
    void desabonnement_themeInexistant_retourne404() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("desabo-inexistant");

        // Act
        ResultActions reponse = seDesabonner(jeton, 999999L);

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce topic n'existe pas"));
    }
}
