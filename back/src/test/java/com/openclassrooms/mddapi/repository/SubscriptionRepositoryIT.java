package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Subscription;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void findByUserId_utilisateurAvecAbonnement_retourneLAbonnementAvecTopicCharge() {
        User user = persistUser("alice@mail.com", "alice");
        Topic topic = persistTopic("Topic IT A", "Description Java");
        entityManager.persistAndFlush(new Subscription(user, topic));
        entityManager.clear();

        List<Subscription> subscriptions = subscriptionRepository.findByUserId(user.getId());

        assertThat(subscriptions).hasSize(1);
        // La session de @DataJpaTest reste ouverte : getTopic() réussirait même sans
        // @EntityGraph (chargement lazy silencieux). On vérifie donc que la relation
        // est déjà initialisée AVANT tout accès.
        assertThat(Hibernate.isInitialized(subscriptions.get(0).getTopic())).isTrue();
        assertThat(subscriptions.get(0).getTopic().getName()).isEqualTo("Topic IT A");
    }
}
