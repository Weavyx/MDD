package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TopicService {
    private final TopicRepository topicRepository;
    private final SubscriptionRepository subscriptionRepository;

    public TopicService(TopicRepository topicRepository, SubscriptionRepository subscriptionRepository) {
        this.topicRepository = topicRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Tous les topics, chacun décoré du statut d'abonnement de l'utilisateur.
     * <p>
     * Deux requêtes fixes quel que soit le nombre de topics : la liste complète, puis les
     * seuls ids de topics suivis ; la jointure se fait en mémoire. Ce choix tient à
     * l'absence d'association {@code Topic → Subscription} dans le modèle. Un utilisateur
     * inexistant obtient la liste avec tous les statuts à {@code false}, pas une erreur.
     * Les topics n'ont pas d'endpoint de création : ils sont insérés directement en base.
     * Seule lecture composite du projet non annotée {@code @Transactional(readOnly = true)}.
     */
    public List<TopicResponse> findAllWithSubscriptionStatus(Long userId) {
        List<Topic> topics = topicRepository.findAll();
        Set<Long> subscribedTopicIds = new HashSet<>(subscriptionRepository.findSubscribedTopicIdsByUserId(userId));

        return topics.stream()
                .map(topic -> new TopicResponse(
                        topic.getId(),
                        topic.getName(),
                        topic.getDescription(),
                        subscribedTopicIds.contains(topic.getId())
                ))
                .toList();
    }
}
