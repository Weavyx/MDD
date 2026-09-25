package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Subscription;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PostRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findPostsByUserId_utilisateurAbonneAUnTopic_neRetourneQueLesPostsDeCeTopicTriesParDate() {
        User user = persistUser("alice@mail.com", "alice");
        Topic subscribedTopic = persistTopic("Topic IT A", "Description Java");
        Topic otherTopic = persistTopic("Topic IT B", "Description Angular");
        entityManager.persistAndFlush(new Subscription(user, subscribedTopic));

        Post olderPost = persistPost("Premier article", "Contenu 1", user, subscribedTopic);
        Post newerPost = persistPost("Second article", "Contenu 2", user, subscribedTopic);
        Post excludedPost = persistPost("Article hors abonnement", "Contenu 3", user, otherTopic);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 10, 0);
        setCreatedAt(olderPost.getId(), base);
        setCreatedAt(newerPost.getId(), base.plusHours(1));
        setCreatedAt(excludedPost.getId(), base.plusMinutes(30));
        entityManager.clear();

        List<Post> desc = postRepository.findPostsByUserId(user.getId(), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Post> asc = postRepository.findPostsByUserId(user.getId(), Sort.by(Sort.Direction.ASC, "createdAt"));

        assertThat(desc).extracting(Post::getId).containsExactly(newerPost.getId(), olderPost.getId());
        assertThat(asc).extracting(Post::getId).containsExactly(olderPost.getId(), newerPost.getId());
        assertThat(desc).noneMatch(post -> post.getId().equals(excludedPost.getId()));
    }

    @Test
    void findPostsByUserId_postEcritParUnAutreAuteurDansUnTopicSouscrit_estInclusDansLeFil() {
        User alice = persistUser("alice@mail.com", "alice");
        User bob = persistUser("bob@mail.com", "bob");
        Topic java = persistTopic("Topic IT A", "Description Java");
        entityManager.persistAndFlush(new Subscription(alice, java));

        // Le fil filtre par topic souscrit, pas par auteur : bob n'est pas abonné et
        // n'est pas alice, son post dans "Topic IT A" doit quand même apparaître chez alice.
        Post postDeBob = persistPost("Article de bob", "Contenu de bob", bob, java);
        entityManager.clear();

        List<Post> feed = postRepository.findPostsByUserId(alice.getId(), Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(feed).extracting(Post::getId).containsExactly(postDeBob.getId());
    }

    @Test
    void findWithUserAndTopicById_postExistant_retourneUserEtTopicChargesSansLazyInitializationException() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Topic IT A", "Description Java");
        Post post = persistPost("Titre", "Contenu", user, topic);
        entityManager.clear();

        Optional<Post> found = postRepository.findWithUserAndTopicById(post.getId());

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

    private void setCreatedAt(Long postId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE posts SET created_at = ? WHERE id = ?", createdAt, postId);
    }
}
