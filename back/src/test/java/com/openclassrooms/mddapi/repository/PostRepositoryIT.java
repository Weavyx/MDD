package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Subscription;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PostRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findPostsByUserId_utilisateurAbonneAUnTopic_neRetourneQueLesPostsDeCeTopicTriesParDate() {
        User user = persistUser("alice@mail.com", "alice");
        Topic subscribedTopic = persistTopic("Java", "Description Java");
        Topic otherTopic = persistTopic("Angular", "Description Angular");
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
    void findWithUserAndTopicById_postExistant_retourneUserEtTopicChargesSansLazyInitializationException() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Java", "Description Java");
        Post post = persistPost("Titre", "Contenu", user, topic);
        entityManager.clear();

        Optional<Post> found = postRepository.findWithUserAndTopicById(post.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUser().getUsername()).isEqualTo("alice");
        assertThat(found.get().getTopic().getName()).isEqualTo("Java");
    }

    private void setCreatedAt(Long postId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE posts SET created_at = ? WHERE id = ?", createdAt, postId);
    }

    private User persistUser(String email, String username) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash("hashed-password");
        entityManager.persistAndFlush(user);
        return user;
    }

    private Topic persistTopic(String name, String description) {
        Topic topic = new Topic(name, description);
        entityManager.persistAndFlush(topic);
        return topic;
    }

    private Post persistPost(String title, String content, User user, Topic topic) {
        Post post = new Post(title, content, user, topic);
        entityManager.persistAndFlush(post);
        return post;
    }
}
