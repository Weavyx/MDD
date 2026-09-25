package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Comment;
import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommentRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findByPostIdOrderByCreatedAtAsc_troisCommentairesInseresDansUnOrdreDifferent_retourneParOrdreChronologique() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Topic IT A", "Description Java");
        Post post = persistPost("Titre", "Contenu", user, topic);
        Post otherPost = persistPost("Autre titre", "Autre contenu", user, topic);

        // Insérés dans l'ordre 1, 2, 3 mais leur created_at réel (fixé ci-dessous) place
        // le commentaire 2 en premier, puis 3, puis 1 : l'ordre d'insertion ne doit pas
        // influencer le résultat, seul created_at compte.
        Comment comment1 = persistComment(user, post, "Commentaire 1 (le plus récent)");
        Comment comment2 = persistComment(user, post, "Commentaire 2 (le plus ancien)");
        Comment comment3 = persistComment(user, post, "Commentaire 3 (intermédiaire)");
        Comment commentOnOtherPost = persistComment(user, otherPost, "Commentaire sur un autre article");

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0);
        setCreatedAt(comment1.getId(), base.plusHours(2));
        setCreatedAt(comment2.getId(), base);
        setCreatedAt(comment3.getId(), base.plusHours(1));
        setCreatedAt(commentOnOtherPost.getId(), base.plusMinutes(30));
        entityManager.clear();

        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId());

        assertThat(comments).extracting(Comment::getId)
                .containsExactly(comment2.getId(), comment3.getId(), comment1.getId());
        // La session de @DataJpaTest reste ouverte : getUser() réussirait même sans
        // @EntityGraph (chargement lazy silencieux). On vérifie donc que la relation
        // est déjà initialisée AVANT tout accès.
        assertThat(comments).allSatisfy(comment -> assertThat(Hibernate.isInitialized(comment.getUser())).isTrue());
        assertThat(comments).allSatisfy(comment -> assertThat(comment.getUser().getUsername()).isEqualTo("alice"));
    }

    private void setCreatedAt(Long commentId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE comments SET created_at = ? WHERE id = ?", createdAt, commentId);
    }

    private Comment persistComment(User user, Post post, String content) {
        Comment comment = new Comment(user, post, content);
        entityManager.persistAndFlush(comment);
        return comment;
    }
}
