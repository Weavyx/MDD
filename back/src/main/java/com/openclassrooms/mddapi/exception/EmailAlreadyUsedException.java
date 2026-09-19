package com.openclassrooms.mddapi.exception;

/**
 * Levée par {@code AuthService.register} et {@code UserService.updateProfile} quand l'email appartient à un autre compte ; traduite en 409. Révèle l'existence d'un compte pour cet email (énumération acceptée, voir la revue technique, axe d).
 */
public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String message) {
        super(message);
    }
}
