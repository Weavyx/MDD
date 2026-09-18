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
