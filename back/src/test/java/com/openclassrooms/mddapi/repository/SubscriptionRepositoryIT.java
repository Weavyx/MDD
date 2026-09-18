package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Subscription;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void existsByUserIdAndTopicId_abonnementExistant_retourneTrue() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Java", "Description Java");
        entityManager.persistAndFlush(new Subscription(user, topic));

        boolean exists = subscriptionRepository.existsByUserIdAndTopicId(user.getId(), topic.getId());

        assertThat(exists).isTrue();
    }

    @Test
    void existsByUserIdAndTopicId_aucunAbonnement_retourneFalse() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Java", "Description Java");

        boolean exists = subscriptionRepository.existsByUserIdAndTopicId(user.getId(), topic.getId());

        assertThat(exists).isFalse();
    }

    @Test
    void findByUserId_utilisateurAvecAbonnement_retourneLAbonnementAvecTopicCharge() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Java", "Description Java");
        entityManager.persistAndFlush(new Subscription(user, topic));
        entityManager.clear();

        List<Subscription> subscriptions = subscriptionRepository.findByUserId(user.getId());

        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getTopic().getName()).isEqualTo("Java");
    }

    @Test
    void findSubscribedTopicIdsByUserId_retourneExactementLesIdsDesTopicsSouscrits() {
        User alice = persistUser("alice@mail.com", "alice");
        User bob = persistUser("bob@mail.com", "bob");
        Topic java = persistTopic("Java", "Description Java");
        Topic angular = persistTopic("Angular", "Description Angular");
        entityManager.persistAndFlush(new Subscription(alice, java));
        entityManager.persistAndFlush(new Subscription(bob, angular));
        entityManager.clear();

        List<Long> ids = subscriptionRepository.findSubscribedTopicIdsByUserId(alice.getId());

        assertThat(ids).containsExactly(java.getId());
    }

    @Test
    void deleteByUserIdAndTopicId_abonnementExistant_supprimeEtRetourne1() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Java", "Description Java");
        entityManager.persistAndFlush(new Subscription(user, topic));

        int deleted = subscriptionRepository.deleteByUserIdAndTopicId(user.getId(), topic.getId());
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(subscriptionRepository.existsByUserIdAndTopicId(user.getId(), topic.getId())).isFalse();
    }

    @Test
    void deleteByUserIdAndTopicId_aucunAbonnement_retourne0() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Java", "Description Java");

        int deleted = subscriptionRepository.deleteByUserIdAndTopicId(user.getId(), topic.getId());

        assertThat(deleted).isEqualTo(0);
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
}
