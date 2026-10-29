package com.openclassrooms.mddapi.security;

import com.openclassrooms.mddapi.security.services.UserDetailsImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Journalise chaque connexion réussie en INFO, avec le seul identifiant interne du compte.
 *
 * <p>Le gestionnaire d'authentification publie l'événement à chaque mot de passe vérifié, donc à
 * chaque {@code POST /api/auth/login} réussi. Le filtre JWT n'en publie aucun : les requêtes
 * authentifiées par jeton ne sont pas journalisées. Le principal est le {@code UserDetailsImpl}
 * chargé par {@code UserDetailsServiceImpl} ; ni son nom d'utilisateur ni l'identifiant saisi ne
 * sont écrits.
 */
@Component
public class AuthenticationSuccessListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationSuccessListener.class);

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof UserDetailsImpl details) {
            Long userId = details.getId();
            LOGGER.info("Connexion réussie : utilisateur {}", userId);
        }
    }
}
