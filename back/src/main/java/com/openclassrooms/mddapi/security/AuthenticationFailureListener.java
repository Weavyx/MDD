package com.openclassrooms.mddapi.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.stereotype.Component;

/**
 * Journalise chaque échec de connexion sans l'identifiant saisi.
 *
 * <p>Spring Security publie l'événement pour un mot de passe faux comme pour un identifiant
 * inconnu ; son principal est l'identifiant saisi, volontairement absent du message.
 */
@Component
public class AuthenticationFailureListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationFailureListener.class);

    @EventListener(AuthenticationFailureBadCredentialsEvent.class)
    public void onAuthenticationFailure() {
        LOGGER.warn("Échec de connexion");
    }
}
