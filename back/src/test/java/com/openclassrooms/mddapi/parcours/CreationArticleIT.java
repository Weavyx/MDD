package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Ajouter un article (choisir le thème associé, définir le
 * titre et le contenu) » ; « Lors de l'ajout d'un article, l'auteur et la date sont définis
 * automatiquement ». Route : {@code POST /api/posts}.
 */
class CreationArticleIT extends ParcoursIT {

    @Autowired
    private PostRepository postRepository;

    /** Act commun : publie {@code corps} pour le porteur de {@code jeton}. */
    private ResultActions publier(String jeton, String corps) throws Exception {
        return mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    /** Corps JSON de {@code POST /api/posts}. */
    private static String corpsArticle(Long idTheme, String titre, String contenu) {
        return "{\"topicId\":" + idTheme + ",\"title\":\"" + titre + "\",\"content\":\"" + contenu + "\"}";
    }

    /** Id de l'article créé, lu dans l'en-tête Location de la réponse. */
    private static Long idDepuisLocation(ResultActions reponse) {
        String location = reponse.andReturn().getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    @Test
    void creation_donneesValides_retourne201EtEnregistreLArticle() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("article-auteur");
        String corps = corpsArticle(idDuTheme("Java"), "Mon titre", "Mon contenu");

        // Act
        ResultActions reponse = publier(jeton, corps);

        // Assert
        reponse.andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/posts/\\d+$")));
        viderLeCache();
        Post article = postRepository.findWithUserAndTopicById(idDepuisLocation(reponse)).orElseThrow();
        assertThat(article.getTitle()).isEqualTo("Mon titre");
        assertThat(article.getContent()).isEqualTo("Mon contenu");
        assertThat(article.getTopic().getName()).isEqualTo("Java");
        assertThat(article.getUser().getUsername()).isEqualTo("article-auteur");
    }

    @Test
    void creation_auteurEtDateDefinisParLeServeur() throws Exception {
        // Arrange : le corps tente d'imposer un auteur et une date ; ils doivent être ignorés
        String jeton = inscrireEtConnecter("article-vrai-auteur");
        String corps = "{\"topicId\":" + idDuTheme("Java") + ",\"title\":\"Titre\",\"content\":\"Contenu\","
                + "\"author\":\"pirate\",\"createdAt\":\"2000-01-01T00:00:00\"}";
        LocalDateTime avant = LocalDateTime.now().minusSeconds(5);

        // Act
        ResultActions reponse = publier(jeton, corps);

        // Assert
        reponse.andExpect(status().isCreated());
        viderLeCache();
        Post article = postRepository.findWithUserAndTopicById(idDepuisLocation(reponse)).orElseThrow();
        assertThat(article.getUser().getUsername()).isEqualTo("article-vrai-auteur");
        assertThat(article.getCreatedAt()).isBetween(avant, LocalDateTime.now().plusSeconds(5));
    }

    @Test
    void creation_themeInexistant_retourne404() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("article-sans-theme");
        String corps = corpsArticle(999999L, "Titre", "Contenu");

        // Act
        ResultActions reponse = publier(jeton, corps);

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce topic n'existe pas"));
    }

    @ParameterizedTest(name = "{index} : {0} refusé")
    @CsvSource(delimiter = '|', textBlock = """
            title   | {"topicId":1,"title":"","content":"Contenu"}
            content | {"topicId":1,"title":"Titre","content":""}
            topicId | {"title":"Titre","content":"Contenu"}
            """)
    void creation_champInvalide_retourne400(String champ, String corps) throws Exception {
        // Arrange : corps fourni par @CsvSource, un champ vide ou absent par cas
        String jeton = inscrireEtConnecter("article-invalide");

        // Act
        ResultActions reponse = publier(jeton, corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.%s", champ).exists());
    }
}
