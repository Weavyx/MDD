package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.Comment;
import com.openclassrooms.mddapi.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Ajouter un commentaire à un article (définir le contenu) » ;
 * « Lors de l'ajout d'un commentaire, l'auteur et la date sont définis automatiquement ».
 * Route : {@code POST /api/posts/{id}/comments}.
 */
class CommentaireIT extends ParcoursIT {

    @Autowired
    private CommentRepository commentRepository;

    /** Act commun : envoie {@code corps} comme commentaire de l'article désigné dans l'URL. */
    private ResultActions commenterArticle(String jeton, String idArticle, String corps) throws Exception {
        return mockMvc.perform(post("/api/posts/" + idArticle + "/comments")
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    @Test
    void commentaire_valide_retourne201EtEnregistreAvecAuteurEtDate() throws Exception {
        // Arrange
        String jetonAuteur = inscrireEtConnecter("comm-auteur");
        Long idArticle = creerArticle(jetonAuteur, idDuTheme("Java"), "Article commente", "Contenu");
        String jetonCommentateur = inscrireEtConnecter("comm-lecteur");
        LocalDateTime avant = LocalDateTime.now().minusSeconds(5);

        // Act
        ResultActions reponse = commenterArticle(jetonCommentateur, idArticle.toString(), "{\"content\":\"Bel article\"}");

        // Assert
        reponse.andExpect(status().isCreated());
        viderLeCache();
        List<Comment> commentaires = commentRepository.findByPostIdOrderByCreatedAtAsc(idArticle);
        assertThat(commentaires).hasSize(1);
        Comment commentaire = commentaires.get(0);
        assertThat(commentaire.getContent()).isEqualTo("Bel article");
        assertThat(commentaire.getUser().getUsername()).isEqualTo("comm-lecteur");
        assertThat(commentaire.getCreatedAt()).isBetween(avant, LocalDateTime.now().plusSeconds(5));
    }

    @Test
    void commentaire_articleInexistant_retourne404() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("comm-inexistant");

        // Act
        ResultActions reponse = commenterArticle(jeton, "999999", "{\"content\":\"Bel article\"}");

        // Assert
        reponse.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet article n'existe pas"));
    }

    static Stream<String> contenusInvalides() {
        return Stream.of("", "a".repeat(1001));
    }

    @ParameterizedTest(name = "contenu invalide n°{index}")
    @MethodSource("contenusInvalides")
    void commentaire_contenuInvalide_retourne400(String contenu) throws Exception {
        // Arrange : contenu vide ou de 1001 caractères
        String jetonAuteur = inscrireEtConnecter("comm-invalide");
        Long idArticle = creerArticle(jetonAuteur, idDuTheme("Java"), "Article", "Contenu");

        // Act
        ResultActions reponse = commenterArticle(jetonAuteur, idArticle.toString(), "{\"content\":\"" + contenu + "\"}");

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.content").exists());
    }
}
