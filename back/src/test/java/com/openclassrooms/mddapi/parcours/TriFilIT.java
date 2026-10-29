package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Trier le fil d'actualité du plus récent au plus ancien ou
 * bien du plus ancien au plus récent ». Route : {@code GET /api/user/feed?sort=asc|desc}.
 */
class TriFilIT extends ActionIT {

    /** Arrange commun : trois articles publiés dans l'ordre 1, 2, 3, et un lecteur abonné connecté. */
    private void lecteurConnecteAvecTroisArticles(String prefixe) {
        User auteur = creerUtilisateur(prefixe + "-auteur");
        Long idJava = idDuTheme("Java");
        creerArticle(auteur, idJava, "Article 1", "Premier");
        creerArticle(auteur, idJava, "Article 2", "Deuxieme");
        creerArticle(auteur, idJava, "Article 3", "Troisieme");
        User lecteur = creerUtilisateur(prefixe + "-lecteur");
        abonner(lecteur, idJava);
        connecterEnTantQue(lecteur);
    }

    /** Act commun : lit le fil de l'utilisateur connecté, trié selon {@code sort}. */
    private ResultActions consulterFilTrie(String sort) throws Exception {
        return mockMvc.perform(get("/api/user/feed").param("sort", sort));
    }

    @Test
    void tri_asc_duPlusAncienAuPlusRecent() throws Exception {
        // Arrange
        lecteurConnecteAvecTroisArticles("tri-asc");

        // Act
        ResultActions reponse = consulterFilTrie("asc");

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Article 1", "Article 2", "Article 3")));
    }

    @Test
    void tri_desc_duPlusRecentAuPlusAncien() throws Exception {
        // Arrange
        lecteurConnecteAvecTroisArticles("tri-desc");

        // Act
        ResultActions reponse = consulterFilTrie("desc");

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Article 3", "Article 2", "Article 1")));
    }

    @Test
    void tri_valeurInvalide_retourne400() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("tri-invalide"));

        // Act
        ResultActions reponse = consulterFilTrie("croissant");

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le paramètre fourni est invalide"));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void tri_valeurInvalide_journaliseLeParametreSansLaValeur(CapturedOutput output) throws Exception {
        // Arrange
        User lecteur = creerUtilisateur("tri-journal");
        connecterEnTantQue(lecteur);

        // Act
        ResultActions reponse = consulterFilTrie("tri-refuse-journal");

        // Assert
        reponse.andExpect(status().isBadRequest());
        assertThat(output.getAll().lines().filter(ligne -> ligne.contains(
                "Échec de validation : GET /api/user/feed ; paramètre sort:Pattern ; utilisateur " + lecteur.getId())))
                .singleElement().asString().contains(" WARN ");
        assertThat(output.getAll()).doesNotContain("tri-refuse-journal");
    }
}
