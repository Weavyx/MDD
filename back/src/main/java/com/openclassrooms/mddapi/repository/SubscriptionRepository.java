package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Subscription;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByUserIdAndTopicId(Long userId, Long topicId);

    @EntityGraph(attributePaths = "topic")
    List<Subscription> findByUserId(Long userId);

    /**
     * Les seuls ids des topics suivis par {@code userId}, sans charger ni les abonnements ni
     * les topics : projection destinée à la jointure en mémoire de
     * {@code TopicService.findAllWithSubscriptionStatus}. Liste vide pour un utilisateur
     * sans abonnement ou inexistant.
     */
    @Query("SELECT s.topic.id FROM Subscription s WHERE s.user.id = :userId")
    List<Long> findSubscribedTopicIdsByUserId(@Param("userId") Long userId);

    /**
     * Supprime en une instruction l'abonnement de {@code userId} au topic {@code topicId},
     * sans le charger. Renvoie le nombre de lignes supprimées (0 ou 1), que l'appelant
     * ignore pour rester idempotent. Requête {@code @Modifying} : doit s'exécuter dans une
     * transaction (fournie par {@code UserService.unsubscribe}) et n'invalide pas le contexte
     * de persistance. La condition sur {@code user_id} est ce qui empêche de désabonner tous
     * les utilisateurs du topic — vérifié par {@code SubscriptionRepositoryIT}.
     */
    @Modifying
    @Query("DELETE FROM Subscription s WHERE s.user.id = :userId AND s.topic.id = :topicId")
    int deleteByUserIdAndTopicId(@Param("userId") Long userId, @Param("topicId") Long topicId);
}
