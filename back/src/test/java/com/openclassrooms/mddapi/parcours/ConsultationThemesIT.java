package com.openclassrooms.mddapi.parcours;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Consulter la liste de tous les thèmes (que l'utilisateur y
 * soit abonné ou non) via une page dédiée ». Route : {@code GET /api/topics}.
 */
class ConsultationThemesIT extends ParcoursIT {

    /** Act commun : lit la liste des thèmes pour le porteur de {@code jeton}. */
    private ResultActions consulterThemes(String jeton) throws Exception {
        return mockMvc.perform(get("/api/topics")
                .header("Authorization", "Bearer " + jeton));
    }

    @Test
    void themes_retourneLesHuitThemesDeReference() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("themes-liste");

        // Act
        ResultActions reponse = consulterThemes(jeton);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(
                        "Java", "JavaScript", "Python", "Web3", "Angular", "Spring", "DevOps", "Sécurité")));
    }

    @Test
    void themes_indiqueLesAbonnementsDuCompte() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("themes-abonne");
        abonner(jeton, idDuTheme("Java"));

        // Act
        ResultActions reponse = consulterThemes(jeton);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Java')].subscribed", contains(true)))
                .andExpect(jsonPath("$[?(@.name == 'Angular')].subscribed", contains(false)));
    }

    @Test
    void themes_abonnementsDUnAutreCompte_nApparaissentPas() throws Exception {
        // Arrange : un autre compte suit Java, pas le compte qui consulte
        String jetonAutre = inscrireEtConnecter("themes-autre");
        abonner(jetonAutre, idDuTheme("Java"));
        String jeton = inscrireEtConnecter("themes-moi");

        // Act
        ResultActions reponse = consulterThemes(jeton);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Java')].subscribed", contains(false)));
    }
}
