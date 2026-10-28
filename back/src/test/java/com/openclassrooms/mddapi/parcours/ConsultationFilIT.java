package com.openclassrooms.mddapi.parcours;

import com.jayway.jsonpath.JsonPath;
import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
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
class ConsultationFilIT extends ActionIT {

    /** Act commun : lit le fil de l'utilisateur connecté, sans paramètre de tri. */
    private ResultActions consulterFil() throws Exception {
        return mockMvc.perform(get("/api/user/feed"));
    }

    @Test
    void fil_contientLesArticlesDesThemesSuivisEtPasLesAutres() throws Exception {
        // Arrange : un article en Java, un en Python ; le lecteur ne suit que Java
        User auteur = creerUtilisateur("fil-auteur");
        Post articleJava = creerArticle(auteur, idDuTheme("Java"), "Article Java", "Contenu Java");
        creerArticle(auteur, idDuTheme("Python"), "Article Python", "Contenu Python");
        User lecteur = creerUtilisateur("fil-lecteur");
        abonner(lecteur, idDuTheme("Java"));
        connecterEnTantQue(lecteur);

        // Act
        ResultActions reponse = consulterFil();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Article Java"))
                .andExpect(jsonPath("$[0].topicName").value("Java"))
                .andExpect(jsonPath("$[0].author").value("fil-auteur"))
                .andExpect(jsonPath("$[0].excerpt").value("Contenu Java"));
        String dateRenvoyee = JsonPath.read(reponse.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$[0].createdAt");
        viderLeCache();
        assertThat(LocalDateTime.parse(dateRenvoyee)).isEqualTo(postRepository.findById(articleJava.getId()).orElseThrow().getCreatedAt());
    }

    @Test
    void fil_parDefaut_duPlusRecentAuPlusAncien() throws Exception {
        // Arrange : trois articles publiés dans l'ordre 1, 2, 3
        User auteur = creerUtilisateur("fil-ordre-auteur");
        Long idJava = idDuTheme("Java");
        creerArticle(auteur, idJava, "Article 1", "Premier");
        creerArticle(auteur, idJava, "Article 2", "Deuxieme");
        creerArticle(auteur, idJava, "Article 3", "Troisieme");
        User lecteur = creerUtilisateur("fil-ordre-lecteur");
        abonner(lecteur, idJava);
        connecterEnTantQue(lecteur);

        // Act
        ResultActions reponse = consulterFil();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Article 3", "Article 2", "Article 1")));
    }

    @Test
    void fil_sansAbonnement_estVide() throws Exception {
        // Arrange : un article existe, mais le lecteur ne suit aucun thème
        creerArticle(creerUtilisateur("fil-vide-auteur"), idDuTheme("Java"), "Article seul", "Contenu");
        connecterEnTantQue(creerUtilisateur("fil-vide-lecteur"));

        // Act
        ResultActions reponse = consulterFil();

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
