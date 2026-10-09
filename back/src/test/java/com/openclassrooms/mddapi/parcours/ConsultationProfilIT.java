package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class ConsultationProfilIT extends ParcoursIT {

    @Autowired
    private JwtService jwtService;

    /** Act commun : lit le profil du porteur de {@code jeton}. */
    private ResultActions consulterProfil(String jeton) throws Exception {
        return mockMvc.perform(get("/api/user")
                .header("Authorization", "Bearer " + jeton));
    }

    @Test
    void consultation_compteSansAbonnement_retourneEmailNomEtListeVide() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("profil-vide");

        // Act
        ResultActions reponse = consulterProfil(jeton);

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
        String jeton = inscrireEtConnecter("profil-abonne");
        abonner(jeton, idDuTheme("Java"));
        abonner(jeton, idDuTheme("Angular"));

        // Act
        ResultActions reponse = consulterProfil(jeton);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions", hasSize(2)))
                .andExpect(jsonPath("$.subscriptions[*].name", containsInAnyOrder("Java", "Angular")))
                .andExpect(jsonPath("$.subscriptions[*].subscribed", everyItem(is(true))));
    }

    @Test
    void consultation_compteInexistant_retourne404() throws Exception {
        // Arrange : jeton valide portant l'id d'un compte qui n'existe pas
        String jeton = jwtService.generateToken("999999");

        // Act
        ResultActions reponse = consulterProfil(jeton);

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet utilisateur n'existe pas"));
    }
}
