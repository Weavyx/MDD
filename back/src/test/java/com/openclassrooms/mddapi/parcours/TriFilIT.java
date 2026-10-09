package com.openclassrooms.mddapi.parcours;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Trier le fil d'actualité du plus récent au plus ancien ou
 * bien du plus ancien au plus récent ». Route : {@code GET /api/user/feed?sort=asc|desc}.
 */
class TriFilIT extends ParcoursIT {

    /** Arrange commun : trois articles publiés dans l'ordre 1, 2, 3 et un lecteur abonné ; renvoie le jeton du lecteur. */
    private String lecteurAvecTroisArticles(String prefixe) throws Exception {
        String jetonAuteur = inscrireEtConnecter(prefixe + "-auteur");
        Long idJava = idDuTheme("Java");
        creerArticle(jetonAuteur, idJava, "Article 1", "Premier");
        creerArticle(jetonAuteur, idJava, "Article 2", "Deuxieme");
        creerArticle(jetonAuteur, idJava, "Article 3", "Troisieme");
        String jetonLecteur = inscrireEtConnecter(prefixe + "-lecteur");
        abonner(jetonLecteur, idJava);
        return jetonLecteur;
    }

    /** Act commun : lit le fil trié selon {@code sort}. */
    private ResultActions consulterFilTrie(String jeton, String sort) throws Exception {
        return mockMvc.perform(get("/api/user/feed")
                .param("sort", sort)
                .header("Authorization", "Bearer " + jeton));
    }

    @Test
    void tri_asc_duPlusAncienAuPlusRecent() throws Exception {
        // Arrange
        String jeton = lecteurAvecTroisArticles("tri-asc");

        // Act
        ResultActions reponse = consulterFilTrie(jeton, "asc");

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Article 1", "Article 2", "Article 3")));
    }

    @Test
    void tri_desc_duPlusRecentAuPlusAncien() throws Exception {
        // Arrange
        String jeton = lecteurAvecTroisArticles("tri-desc");

        // Act
        ResultActions reponse = consulterFilTrie(jeton, "desc");

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Article 3", "Article 2", "Article 1")));
    }

    @Test
    void tri_valeurInvalide_retourne400() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("tri-invalide");

        // Act
        ResultActions reponse = consulterFilTrie(jeton, "croissant");

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le paramètre fourni est invalide"));
    }
}
