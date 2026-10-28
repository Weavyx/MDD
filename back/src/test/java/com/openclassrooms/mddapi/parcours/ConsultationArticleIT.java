package com.openclassrooms.mddapi.parcours;

import com.jayway.jsonpath.JsonPath;
import com.openclassrooms.mddapi.model.Comment;
import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Consulter un article (thème associé, titre, auteur, date,
 * contenu, commentaires) ». Route : {@code GET /api/posts/{id}}.
 */
class ConsultationArticleIT extends ActionIT {

    /** Act commun : lit l'article désigné dans l'URL. */
    private ResultActions consulterArticle(String idArticle) throws Exception {
        return mockMvc.perform(get("/api/posts/" + idArticle));
    }

    @Test
    void consultation_articleAvecCommentaires_retourneToutesLesInformations() throws Exception {
        // Arrange : un article et deux commentaires publiés dans cet ordre
        User auteur = creerUtilisateur("lecture-auteur");
        Post article = creerArticle(auteur, idDuTheme("Spring"), "Titre lu", "Contenu lu");
        User lecteur = creerUtilisateur("lecture-lecteur");
        commenter(lecteur, article, "Premier commentaire");
        commenter(auteur, article, "Second commentaire");
        connecterEnTantQue(lecteur);

        // Act
        ResultActions reponse = consulterArticle(article.getId().toString());

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre lu"))
                .andExpect(jsonPath("$.content").value("Contenu lu"))
                .andExpect(jsonPath("$.topicName").value("Spring"))
                .andExpect(jsonPath("$.author").value("lecture-auteur"))
                .andExpect(jsonPath("$.comments", hasSize(2)))
                .andExpect(jsonPath("$.comments[*].content", contains("Premier commentaire", "Second commentaire")))
                .andExpect(jsonPath("$.comments[*].author", contains("lecture-lecteur", "lecture-auteur")));
        String corps = reponse.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String dateRenvoyee = JsonPath.read(corps, "$.createdAt");
        List<String> datesDesCommentaires = JsonPath.read(corps, "$.comments[*].createdAt");
        viderLeCache();
        assertThat(LocalDateTime.parse(dateRenvoyee)).isEqualTo(postRepository.findById(article.getId()).orElseThrow().getCreatedAt());
        assertThat(datesDesCommentaires.stream().map(LocalDateTime::parse).toList())
                .isEqualTo(commentRepository.findByPostIdOrderByCreatedAtAsc(article.getId()).stream().map(Comment::getCreatedAt).toList());
    }

    @Test
    void consultation_articleInexistant_retourne404() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("lecture-inexistant"));

        // Act
        ResultActions reponse = consulterArticle("999999");

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet article n'existe pas"));
    }

    @Test
    void consultation_identifiantNonNumerique_retourne400() throws Exception {
        // Arrange
        connecterEnTantQue(creerUtilisateur("lecture-abc"));

        // Act
        ResultActions reponse = consulterArticle("abc");

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Le paramètre fourni est invalide"));
    }
}
