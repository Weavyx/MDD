package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.exception.AlreadySubscribedException;
import com.openclassrooms.mddapi.exception.TopicNotFoundException;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicServiceTest {

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserRepository userRepository;

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

        assertThat(result).extracting(TopicResponse::getId, TopicResponse::isSubscribed)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, false),
                        org.assertj.core.groups.Tuple.tuple(2L, true),
                        org.assertj.core.groups.Tuple.tuple(3L, true)
                );
    }

    @Test
    void subscribe_topicExistantSansAbonnementPrealable_saveAppeleUneFois() {
        Topic topic = new Topic("Java", "Description Java");
        setId(topic, 1L);
        User user = new User();
        user.setId(10L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(subscriptionRepository.existsByUserIdAndTopicId(10L, 1L)).thenReturn(false);
        when(userRepository.getReferenceById(10L)).thenReturn(user);

        topicService.subscribe(10L, 1L);

        verify(subscriptionRepository, times(1)).save(any());
    }

    @Test
    void subscribe_topicInexistant_topicNotFoundExceptionEtSaveJamaisAppele() {
        when(topicRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TopicNotFoundException.class, () -> topicService.subscribe(10L, 99L));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribe_abonnementDejaExistant_alreadySubscribedExceptionEtSaveJamaisAppele() {
        Topic topic = new Topic("Java", "Description Java");
        setId(topic, 1L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(subscriptionRepository.existsByUserIdAndTopicId(10L, 1L)).thenReturn(true);

        assertThrows(AlreadySubscribedException.class, () -> topicService.subscribe(10L, 1L));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void unsubscribe_topicExistantAvecAbonnement_deleteAppeleUneFoisSansException() {
        Topic topic = new Topic("Java", "Description Java");
        setId(topic, 1L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        topicService.unsubscribe(10L, 1L);

        verify(subscriptionRepository, times(1)).deleteByUserIdAndTopicId(10L, 1L);
    }

    @Test
    void unsubscribe_topicExistantSansAbonnement_deleteAppeleQuandMemeIdempotence() {
        Topic topic = new Topic("Java", "Description Java");
        setId(topic, 1L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(subscriptionRepository.deleteByUserIdAndTopicId(10L, 1L)).thenReturn(0);

        topicService.unsubscribe(10L, 1L);

        verify(subscriptionRepository, times(1)).deleteByUserIdAndTopicId(10L, 1L);
    }

    @Test
    void unsubscribe_topicInexistant_topicNotFoundExceptionEtDeleteJamaisAppele() {
        when(topicRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TopicNotFoundException.class, () -> topicService.unsubscribe(10L, 99L));

        verify(subscriptionRepository, never()).deleteByUserIdAndTopicId(anyLong(), anyLong());
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
