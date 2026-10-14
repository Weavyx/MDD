package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chargement anticipé du détail d'un article ({@code @EntityGraph} de
 * {@code findWithUserAndTopicById}). Les tests d'intégration ne peuvent pas l'observer : dans
 * leur transaction ouverte, un chargement paresseux réussit aussi. Seul un test de persistance
 * qui inspecte l'état des relations le prouve.
 */
class PostRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private PostRepository postRepository;

    @Test
    void findWithUserAndTopicById_postExistant_retourneUserEtTopicChargesSansLazyInitializationException() {
        // Arrange
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Topic IT A", "Description Java");
        Post post = persistPost("Titre", "Contenu", user, topic);
        entityManager.clear();

        // Act
        Optional<Post> found = postRepository.findWithUserAndTopicById(post.getId());

        // Assert
        assertThat(found).isPresent();
        // La session de @DataJpaTest reste ouverte : un simple accès à getUser()
        // réussirait même sans @EntityGraph (chargement lazy silencieux). On vérifie
        // donc que les relations sont déjà initialisées AVANT tout accès, ce qui ne
        // peut venir que du fetch anticipé de la requête.
        assertThat(Hibernate.isInitialized(found.get().getUser())).isTrue();
        assertThat(Hibernate.isInitialized(found.get().getTopic())).isTrue();
        assertThat(found.get().getUser().getUsername()).isEqualTo("alice");
        assertThat(found.get().getTopic().getName()).isEqualTo("Topic IT A");
    }
}
