package com.openclassrooms.mddapi.security;

import com.openclassrooms.mddapi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

/**
 * Journalise chaque échec de connexion en WARN, avec l'identifiant interne du compte visé s'il
 * existe, « compte inconnu » sinon.
 *
 * <p>Spring Security publie l'événement pour un mot de passe faux comme pour un identifiant
 * inconnu ; son principal est le texte saisi (e-mail ou nom d'utilisateur). Ce texte sert
 * seulement à retrouver le compte, par la même requête que {@code UserDetailsServiceImpl} : il
 * n'est jamais écrit, et l'exception de l'événement, dont la cause peut le contenir, n'est jamais
 * passée au logger.
 */
@Component
public class AuthenticationFailureListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFailureListener.class);

    private final UserRepository userRepository;

    public AuthenticationFailureListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String account = userRepository.findByEmailOrUsername(event.getAuthentication().getName())
                .map(user -> "utilisateur " + user.getId())
                .orElse("compte inconnu");
        LOGGER.warn("Échec de connexion : {}", account);
    }
}
