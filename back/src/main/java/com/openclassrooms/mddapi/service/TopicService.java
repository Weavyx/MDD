package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.exception.AlreadySubscribedException;
import com.openclassrooms.mddapi.exception.TopicNotFoundException;
import com.openclassrooms.mddapi.model.Subscription;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class TopicService {
    private final TopicRepository topicRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    public TopicService(TopicRepository topicRepository, SubscriptionRepository subscriptionRepository, UserRepository userRepository) {
        this.topicRepository = topicRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
    }

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

    @Transactional
    public void subscribe(Long userId, Long topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Ce topic n'existe pas"));

        if (subscriptionRepository.existsByUserIdAndTopicId(userId, topicId)) {
            throw new AlreadySubscribedException("Vous êtes déjà abonné à ce topic");
        }

        User user = userRepository.getReferenceById(userId);
        Subscription subscription = new Subscription(user, topic);
        subscriptionRepository.save(subscription);
    }

    @Transactional
    public void unsubscribe(Long userId, Long topicId) {
        subscriptionRepository.deleteByUserIdAndTopicId(userId, topicId);
    }
}
