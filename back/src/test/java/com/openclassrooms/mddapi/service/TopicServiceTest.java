package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private TopicService topicService;

    @Test
    void findAllWithSubscriptionStatus_aucunAbonnement_tousLesTopicsNonSouscrits() {
        Topic topic1 = new Topic("Java", "Description Java");
        Topic topic2 = new Topic("Angular", "Description Angular");
        when(topicRepository.findAll()).thenReturn(List.of(topic1, topic2));
        when(subscriptionRepository.findSubscribedTopicIdsByUserId(1L)).thenReturn(List.of());

        List<TopicResponse> result = topicService.findAllWithSubscriptionStatus(1L);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(response -> !response.isSubscribed());
    }

    @Test
    void findAllWithSubscriptionStatus_abonneAUnSousEnsemble_flagSubscribedCorrectParTopic() {
        Topic topic1 = new Topic("Java", "Description Java");
        Topic topic2 = new Topic("Angular", "Description Angular");
        Topic topic3 = new Topic("Spring", "Description Spring");
        setId(topic1, 1L);
        setId(topic2, 2L);
        setId(topic3, 3L);
        when(topicRepository.findAll()).thenReturn(List.of(topic1, topic2, topic3));
        when(subscriptionRepository.findSubscribedTopicIdsByUserId(42L)).thenReturn(List.of(2L, 3L));

        List<TopicResponse> result = topicService.findAllWithSubscriptionStatus(42L);

        assertThat(result).extracting(TopicResponse::getId, TopicResponse::getName, TopicResponse::getDescription, TopicResponse::isSubscribed)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, "Java", "Description Java", false),
                        org.assertj.core.groups.Tuple.tuple(2L, "Angular", "Description Angular", true),
                        org.assertj.core.groups.Tuple.tuple(3L, "Spring", "Description Spring", true)
                );
    }

    private void setId(Topic topic, Long id) {
        try {
            java.lang.reflect.Field field = Topic.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(topic, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
