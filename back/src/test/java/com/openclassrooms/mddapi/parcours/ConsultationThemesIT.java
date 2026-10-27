package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.User;
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
class ConsultationThemesIT extends ActionIT {

    /** Act commun : lit la liste des thèmes pour l'utilisateur connecté. */
    private ResultActions consulterThemes() throws Exception {
        return mockMvc.perform(get("/api/topics"));
    }

    @Test
    void themes_retourneLesHuitThemesDeReference() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("themes-liste"));

        // Act
        ResultActions reponse = consulterThemes();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder(
                        "Java", "JavaScript", "Python", "Web3", "Angular", "Spring", "DevOps", "Sécurité")));
    }

    @Test
    void themes_indiqueLesAbonnementsDuCompte() throws Exception {
        // Arrange
        User compte = creerUtilisateur("themes-abonne");
        abonner(compte, idDuTheme("Java"));
        connecterEnTantQue(compte);

        // Act
        ResultActions reponse = consulterThemes();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Java')].subscribed", contains(true)))
                .andExpect(jsonPath("$[?(@.name == 'Angular')].subscribed", contains(false)));
    }

    @Test
    void themes_abonnementsDUnAutreCompte_nApparaissentPas() throws Exception {
        // Arrange : un autre compte suit Java, pas le compte qui consulte
        abonner(creerUtilisateur("themes-autre"), idDuTheme("Java"));
        connecterEnTantQue(creerUtilisateur("themes-moi"));

        // Act
        ResultActions reponse = consulterThemes();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Java')].subscribed", contains(false)));
    }
}
