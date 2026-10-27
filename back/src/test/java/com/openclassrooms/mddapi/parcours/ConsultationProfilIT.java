package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Consulter son profil (e-mail, nom d'utilisateur et
 * abonnements) via la page de profil ». Route : {@code GET /api/user}.
 */
class ConsultationProfilIT extends ActionIT {

    /** Act commun : lit le profil de l'utilisateur connecté. */
    private ResultActions consulterProfil() throws Exception {
        return mockMvc.perform(get("/api/user"));
    }

    @Test
    void consultation_compteSansAbonnement_retourneEmailNomEtListeVide() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("profil-vide"));

        // Act
        ResultActions reponse = consulterProfil();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profil-vide@mail.com"))
                .andExpect(jsonPath("$.username").value("profil-vide"))
                .andExpect(jsonPath("$.subscriptions", hasSize(0)))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void consultation_compteAbonne_retourneLesThemesSuivis() throws Exception {
        // Arrange
        User compte = creerUtilisateur("profil-abonne");
        abonner(compte, idDuTheme("Java"));
        abonner(compte, idDuTheme("Angular"));
        connecterEnTantQue(compte);

        // Act
        ResultActions reponse = consulterProfil();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions", hasSize(2)))
                .andExpect(jsonPath("$.subscriptions[*].name", containsInAnyOrder("Java", "Angular")))
                .andExpect(jsonPath("$.subscriptions[*].subscribed", everyItem(is(true))));
    }

    @Test
    void consultation_compteInexistant_retourne404() throws Exception {
        // Arrange : utilisateur connecté portant l'id d'un compte qui n'existe pas
        connecterEnTantQue(999999L);

        // Act
        ResultActions reponse = consulterProfil();

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet utilisateur n'existe pas"));
    }
}
