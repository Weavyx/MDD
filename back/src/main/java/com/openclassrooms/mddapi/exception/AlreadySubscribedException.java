package com.openclassrooms.mddapi.exception;

/**
 * Levée par {@code UserService.subscribe} quand l'abonnement existe déjà ; traduite en 409. La course entre deux abonnements simultanés n'est pas couverte par ce contrôle mais par la contrainte {@code UNIQUE(user_id, topic_id)}.
 */
public class AlreadySubscribedException extends RuntimeException {
    public AlreadySubscribedException(String message) {
        super(message);
    }
}
