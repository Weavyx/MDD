package com.openclassrooms.mddapi.exception;

/**
 * Levée par {@code PostService.create} (topicId du corps) et par {@code UserService.subscribe}/{@code unsubscribe} (topicId de l'URL) ; traduite en 404. Sur le désabonnement, c'est le seul cas d'erreur : l'absence d'abonnement n'en est pas un.
 */
public class TopicNotFoundException extends RuntimeException {
    public TopicNotFoundException(String message) {
        super(message);
    }
}
