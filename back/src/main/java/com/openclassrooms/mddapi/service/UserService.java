package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.dto.UpdateProfileRequest;
import com.openclassrooms.mddapi.dto.UserProfileResponse;
import com.openclassrooms.mddapi.dto.UserResponse;
import com.openclassrooms.mddapi.exception.EmailAlreadyUsedException;
import com.openclassrooms.mddapi.exception.UserNotFoundException;
import com.openclassrooms.mddapi.exception.UsernameAlreadyUsedException;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, SubscriptionRepository subscriptionRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.passwordEncoder = passwordEncoder;
    }

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
        if (requestPassword != null && !requestPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(requestPassword));
        }

        User savedUser = userRepository.save(user);
        return new UserResponse(savedUser.getId(), savedUser.getEmail(), savedUser.getUsername());
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Cet utilisateur n'existe pas"));
    }
}
