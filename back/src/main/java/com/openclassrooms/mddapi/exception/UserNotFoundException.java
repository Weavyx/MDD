package com.openclassrooms.mddapi.exception;

/**
 * Levée par {@code UserService.getProfile} et {@code updateProfile} quand l'id porté par un JWT valide ne correspond plus à aucun compte (compte supprimé après l'émission du jeton) ; traduite en 404, pas en 401. Jamais levée à la connexion, qui passe par {@code UsernameNotFoundException}.
 */
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
