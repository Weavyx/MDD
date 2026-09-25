package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.dto.UpdateProfileRequest;
import com.openclassrooms.mddapi.dto.UserProfileResponse;
import com.openclassrooms.mddapi.dto.UserResponse;
import com.openclassrooms.mddapi.exception.AlreadySubscribedException;
import com.openclassrooms.mddapi.exception.EmailAlreadyUsedException;
import com.openclassrooms.mddapi.exception.TopicNotFoundException;
import com.openclassrooms.mddapi.exception.UserNotFoundException;
import com.openclassrooms.mddapi.exception.UsernameAlreadyUsedException;
import com.openclassrooms.mddapi.model.Subscription;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void getProfile_utilisateurSansAbonnement_retourneProfilAvecListeVide() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L)).thenReturn(List.of());

        UserProfileResponse result = userService.getProfile(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice@mail.com");
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getSubscriptions()).isEmpty();
    }

    @Test
    void getProfile_utilisateurAvecAbonnements_retourneProfilAvecTopicsSouscrits() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        Topic topic1 = new Topic("Java", "Description Java");
        Topic topic2 = new Topic("Angular", "Description Angular");
        setId(topic1, 10L);
        setId(topic2, 20L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(subscriptionRepository.findByUserId(1L))
                .thenReturn(List.of(new Subscription(user, topic1), new Subscription(user, topic2)));

        UserProfileResponse result = userService.getProfile(1L);

        assertThat(result.getSubscriptions())
                .extracting(TopicResponse::getId, TopicResponse::getName, TopicResponse::getDescription, TopicResponse::isSubscribed)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(10L, "Java", "Description Java", true),
                        org.assertj.core.groups.Tuple.tuple(20L, "Angular", "Description Angular", true)
                );
    }

    @Test
    void getProfile_utilisateurInexistant_userNotFoundException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getProfile(99L));

        verify(subscriptionRepository, never()).findByUserId(anyLong());
    }

    @Test
    void updateProfile_emailEtUsernameSeuls_motDePasseInchangeEtSaveAppele() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        UpdateProfileRequest request = buildRequest("alice2@mail.com", "alice2", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("alice2@mail.com", 1L)).thenReturn(false);
        when(userRepository.existsByUsernameAndIdNot("alice2", 1L)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        UserResponse result = userService.updateProfile(1L, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEmail()).isEqualTo("alice2@mail.com");
        assertThat(result.getUsername()).isEqualTo("alice2");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateProfile_avecNouveauMotDePasse_hashRemplace() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        UpdateProfileRequest request = buildRequest("alice@mail.com", "alice", "NewPass1!");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("newHash");
        when(userRepository.save(user)).thenReturn(user);

        userService.updateProfile(1L, request);

        assertThat(user.getPasswordHash()).isEqualTo("newHash");
        verify(passwordEncoder, times(1)).encode("NewPass1!");
    }

    @Test
    void updateProfile_emailEtUsernameInchanges_aucuneVerificationUniciteEtSaveAppele() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        UpdateProfileRequest request = buildRequest("alice@mail.com", "alice", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        UserResponse result = userService.updateProfile(1L, request);

        assertThat(result.getEmail()).isEqualTo("alice@mail.com");
        assertThat(result.getUsername()).isEqualTo("alice");
        verify(userRepository, never()).existsByEmailAndIdNot(anyString(), anyLong());
        verify(userRepository, never()).existsByUsernameAndIdNot(anyString(), anyLong());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateProfile_emailEtUsernamePrisParUtilisateurLuiMeme_pasDeConflitGraceAIdNot() {
        // Variation de casse : la comparaison Java diffère, mais en base (collation
        // insensible à la casse) seul l'utilisateur courant porte cet email / username ;
        // la clause AndIdNot doit donc l'exclure et ne signaler aucun conflit.
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        UpdateProfileRequest request = buildRequest("Alice@Mail.com", "Alice", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("Alice@Mail.com", 1L)).thenReturn(false);
        when(userRepository.existsByUsernameAndIdNot("Alice", 1L)).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        UserResponse result = userService.updateProfile(1L, request);

        assertThat(result.getEmail()).isEqualTo("Alice@Mail.com");
        assertThat(result.getUsername()).isEqualTo("Alice");
        verify(userRepository).existsByEmailAndIdNot("Alice@Mail.com", 1L);
        verify(userRepository).existsByUsernameAndIdNot("Alice", 1L);
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void updateProfile_emailDejaPrisParUnAutre_emailAlreadyUsedExceptionEtSaveJamaisAppele() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        UpdateProfileRequest request = buildRequest("bob@mail.com", "alice", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmailAndIdNot("bob@mail.com", 1L)).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class, () -> userService.updateProfile(1L, request));

        assertThat(user.getEmail()).isEqualTo("alice@mail.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_usernameDejaPrisParUnAutre_usernameAlreadyUsedExceptionEtSaveJamaisAppele() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");
        UpdateProfileRequest request = buildRequest("alice@mail.com", "bob", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameAndIdNot("bob", 1L)).thenReturn(true);

        assertThrows(UsernameAlreadyUsedException.class, () -> userService.updateProfile(1L, request));

        assertThat(user.getUsername()).isEqualTo("alice");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_utilisateurInexistant_userNotFoundExceptionEtSaveJamaisAppele() {
        UpdateProfileRequest request = buildRequest("alice@mail.com", "alice", null);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.updateProfile(99L, request));

        verify(userRepository, never()).save(any());
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

        userService.subscribe(10L, 1L);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getTopic()).isSameAs(topic);
    }

    @Test
    void subscribe_topicInexistant_topicNotFoundExceptionEtSaveJamaisAppele() {
        when(topicRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TopicNotFoundException.class, () -> userService.subscribe(10L, 99L));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void subscribe_abonnementDejaExistant_alreadySubscribedExceptionEtSaveJamaisAppele() {
        Topic topic = new Topic("Java", "Description Java");
        setId(topic, 1L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(subscriptionRepository.existsByUserIdAndTopicId(10L, 1L)).thenReturn(true);

        assertThrows(AlreadySubscribedException.class, () -> userService.subscribe(10L, 1L));

        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void unsubscribe_topicExistantAvecAbonnement_deleteAppeleUneFoisSansException() {
        Topic topic = new Topic("Java", "Description Java");
        setId(topic, 1L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));

        userService.unsubscribe(10L, 1L);

        verify(subscriptionRepository, times(1)).deleteByUserIdAndTopicId(10L, 1L);
    }

    @Test
    void unsubscribe_topicExistantSansAbonnement_deleteAppeleQuandMemeIdempotence() {
        Topic topic = new Topic("Java", "Description Java");
        setId(topic, 1L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(subscriptionRepository.deleteByUserIdAndTopicId(10L, 1L)).thenReturn(0);

        userService.unsubscribe(10L, 1L);

        verify(subscriptionRepository, times(1)).deleteByUserIdAndTopicId(10L, 1L);
    }

    @Test
    void unsubscribe_topicInexistant_topicNotFoundExceptionEtDeleteJamaisAppele() {
        when(topicRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(TopicNotFoundException.class, () -> userService.unsubscribe(10L, 99L));

        verify(subscriptionRepository, never()).deleteByUserIdAndTopicId(anyLong(), anyLong());
    }

    private User buildUser(Long id, String email, String username, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        return user;
    }

    private UpdateProfileRequest buildRequest(String email, String username, String password) {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword(password);
        return request;
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
