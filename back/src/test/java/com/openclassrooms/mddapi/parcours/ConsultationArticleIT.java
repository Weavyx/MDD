package com.openclassrooms.mddapi.parcours;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Consulter un article (thème associé, titre, auteur, date,
 * contenu, commentaires) ». Route : {@code GET /api/posts/{id}}.
 */
class ConsultationArticleIT extends ParcoursIT {

    /** Act commun : lit l'article désigné dans l'URL. */
    private ResultActions consulterArticle(String jeton, String idArticle) throws Exception {
        return mockMvc.perform(get("/api/posts/" + idArticle)
                .header("Authorization", "Bearer " + jeton));
    }

    @Test
    void consultation_articleAvecCommentaires_retourneToutesLesInformations() throws Exception {
        // Arrange : un article et deux commentaires publiés dans cet ordre
        String jetonAuteur = inscrireEtConnecter("lecture-auteur");
        Long idArticle = creerArticle(jetonAuteur, idDuTheme("Spring"), "Titre lu", "Contenu lu");
        String jetonLecteur = inscrireEtConnecter("lecture-lecteur");
        commenter(jetonLecteur, idArticle, "Premier commentaire");
        commenter(jetonAuteur, idArticle, "Second commentaire");

        // Act
        ResultActions reponse = consulterArticle(jetonLecteur, idArticle.toString());

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre lu"))
                .andExpect(jsonPath("$.content").value("Contenu lu"))
                .andExpect(jsonPath("$.topicName").value("Spring"))
                .andExpect(jsonPath("$.author").value("lecture-auteur"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.comments", hasSize(2)))
                .andExpect(jsonPath("$.comments[*].content", contains("Premier commentaire", "Second commentaire")))
                .andExpect(jsonPath("$.comments[*].author", contains("lecture-lecteur", "lecture-auteur")));
    }

    @Test
    void consultation_articleInexistant_retourne404() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("lecture-inexistant");

        // Act
        ResultActions reponse = consulterArticle(jeton, "999999");

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet article n'existe pas"));
    }

    @Test
    void consultation_identifiantNonNumerique_retourne400() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("lecture-abc");

        // Act
        ResultActions reponse = consulterArticle(jeton, "abc");

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le paramètre fourni est invalide"));
    }
}
