package com.openclassrooms.mddapi.parcours;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Consulter son fil d'actualité sur la page d'accueil par
 * chronologie (du plus récent au plus ancien) une fois connecté ».
 * Route : {@code GET /api/user/feed} (sans paramètre : ordre par défaut).
 */
class ConsultationFilIT extends ParcoursIT {

    /** Act commun : lit le fil du porteur de {@code jeton}, sans paramètre de tri. */
    private ResultActions consulterFil(String jeton) throws Exception {
        return mockMvc.perform(get("/api/user/feed")
                .header("Authorization", "Bearer " + jeton));
    }

    @Test
    void fil_contientLesArticlesDesThemesSuivisEtPasLesAutres() throws Exception {
        // Arrange : un article en Java, un en Python ; le lecteur ne suit que Java
        String jetonAuteur = inscrireEtConnecter("fil-auteur");
        creerArticle(jetonAuteur, idDuTheme("Java"), "Article Java", "Contenu Java");
        creerArticle(jetonAuteur, idDuTheme("Python"), "Article Python", "Contenu Python");
        String jetonLecteur = inscrireEtConnecter("fil-lecteur");
        abonner(jetonLecteur, idDuTheme("Java"));

        // Act
        ResultActions reponse = consulterFil(jetonLecteur);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Article Java"))
                .andExpect(jsonPath("$[0].topicName").value("Java"))
                .andExpect(jsonPath("$[0].author").value("fil-auteur"))
                .andExpect(jsonPath("$[0].excerpt").value("Contenu Java"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void fil_parDefaut_duPlusRecentAuPlusAncien() throws Exception {
        // Arrange : trois articles publiés dans l'ordre 1, 2, 3
        String jetonAuteur = inscrireEtConnecter("fil-ordre-auteur");
        Long idJava = idDuTheme("Java");
        creerArticle(jetonAuteur, idJava, "Article 1", "Premier");
        creerArticle(jetonAuteur, idJava, "Article 2", "Deuxieme");
        creerArticle(jetonAuteur, idJava, "Article 3", "Troisieme");
        String jetonLecteur = inscrireEtConnecter("fil-ordre-lecteur");
        abonner(jetonLecteur, idJava);

        // Act
        ResultActions reponse = consulterFil(jetonLecteur);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Article 3", "Article 2", "Article 1")));
    }

    @Test
    void fil_sansAbonnement_estVide() throws Exception {
        // Arrange : un article existe, mais le lecteur ne suit aucun thème
        String jetonAuteur = inscrireEtConnecter("fil-vide-auteur");
        creerArticle(jetonAuteur, idDuTheme("Java"), "Article seul", "Contenu");
        String jetonLecteur = inscrireEtConnecter("fil-vide-lecteur");

        // Act
        ResultActions reponse = consulterFil(jetonLecteur);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
