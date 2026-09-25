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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Profil et abonnements de l'utilisateur courant.
 * <p>
 * Toutes les méthodes reçoivent un {@code userId} qui provient du claim {@code sub} d'un
 * JWT déjà validé par la chaîne de sécurité : elles ne vérifient pas que l'appelant a le
 * droit d'agir sur cet utilisateur, seulement que l'utilisateur ou le topic visé existe
 * encore. Un compte supprimé entre l'émission du jeton et l'appel se traduit donc par une
 * {@link UserNotFoundException} (404), pas par un 401.
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TopicRepository topicRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, SubscriptionRepository subscriptionRepository, TopicRepository topicRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.topicRepository = topicRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Profil avec la liste des abonnements, en deux requêtes fixes (utilisateur, puis
     * abonnements avec leur topic chargé par {@code @EntityGraph}) quel que soit le
     * nombre d'abonnements. Chaque {@link TopicResponse} renvoyé a {@code subscribed}
     * à {@code true} par construction.
     *
     * @throws UserNotFoundException si aucun utilisateur ne porte cet id (404)
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = findUser(userId);

        List<TopicResponse> subscriptions = subscriptionRepository.findByUserId(userId).stream()
                .map(subscription -> {
                    Topic topic = subscription.getTopic();
                    return new TopicResponse(topic.getId(), topic.getName(), topic.getDescription(), true);
                })
                .toList();

        return new UserProfileResponse(user.getId(), user.getEmail(), user.getUsername(), subscriptions);
    }

    /**
     * Remplace email et nom d'utilisateur, et le mot de passe seulement s'il est fourni.
     * <p>
     * Contrat :
     * <ul>
     *   <li>un mot de passe {@code null} (champ absent du JSON) laisse le hash existant
     *       intact ; toute autre valeur est ré-encodée avec BCrypt et remplace l'ancien
     *       hash — l'ancien mot de passe n'est jamais demandé, le JWT tenant lieu de preuve
     *       d'identité. Un mot de passe vide ou blanc n'arrive jamais ici : la validation de
     *       {@code UpdateProfileRequest} le rejette en 400 ;</li>
     *   <li>l'unicité de l'email et du nom d'utilisateur n'est vérifiée que si la valeur
     *       change, et en excluant l'utilisateur lui-même : renvoyer sa propre valeur ne
     *       produit pas de conflit ;</li>
     *   <li>la vérification d'unicité et l'enregistrement ne sont pas atomiques entre deux
     *       requêtes concurrentes ; la contrainte {@code UNIQUE} en base tranche alors par
     *       une {@code DataIntegrityViolationException} (409 générique).</li>
     * </ul>
     *
     * @throws UserNotFoundException      si aucun utilisateur ne porte cet id (404)
     * @throws EmailAlreadyUsedException  si le nouvel email appartient à un autre compte (409)
     * @throws UsernameAlreadyUsedException si le nouveau nom appartient à un autre compte (409)
     */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUser(userId);
        String requestEmail = request.getEmail();
        String requestUsername = request.getUsername();
        String requestPassword = request.getPassword();

        if (!requestEmail.equals(user.getEmail()) && userRepository.existsByEmailAndIdNot(requestEmail, userId)) {
            throw new EmailAlreadyUsedException("Cet email est déjà utilisé");
        }
        if (!requestUsername.equals(user.getUsername()) && userRepository.existsByUsernameAndIdNot(requestUsername, userId)) {
            throw new UsernameAlreadyUsedException("Ce nom d'utilisateur est déjà utilisé");
        }

        user.setEmail(requestEmail);
        user.setUsername(requestUsername);
        if (requestPassword != null) {
            user.setPasswordHash(passwordEncoder.encode(requestPassword));
        }

        User savedUser = userRepository.save(user);
        return new UserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getUsername());
    }

    /**
     * Crée l'abonnement de l'utilisateur au topic.
     * <p>
     * L'existence du topic est vérifiée par un {@code SELECT} (pour produire un 404 fiable) ;
     * celle de l'utilisateur ne l'est pas : {@code getReferenceById} fournit un proxy sans
     * requête, l'id venant d'un JWT validé. Si le compte a été supprimé entre-temps, la
     * violation de clé étrangère au {@code flush} remonte en
     * {@code DataIntegrityViolationException} (409), pas en 404. Le double abonnement est
     * refusé avant l'insertion, et à défaut par la contrainte {@code UNIQUE(user_id, topic_id)}.
     *
     * @throws TopicNotFoundException     si le topic n'existe pas (404)
     * @throws AlreadySubscribedException si l'abonnement existe déjà (409)
     */
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

    /**
     * Supprime l'abonnement en une seule instruction {@code DELETE}, sans charger l'entité.
     * <p>
     * Idempotent : l'absence d'abonnement n'est pas une erreur, le nombre de lignes
     * supprimées est ignoré. Seul un topic inexistant est refusé, afin qu'un identifiant
     * erroné ne soit pas confondu avec un désabonnement réussi.
     *
     * @throws TopicNotFoundException si le topic n'existe pas (404)
     */
    @Transactional
    public void unsubscribe(Long userId, Long topicId) {
        topicRepository.findById(topicId)
                .orElseThrow(() -> new TopicNotFoundException("Ce topic n'existe pas"));

        subscriptionRepository.deleteByUserIdAndTopicId(userId, topicId);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Cet utilisateur n'existe pas"));
    }
}
