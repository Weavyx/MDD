package com.openclassrooms.mddapi.exception;

/**
 * Levée par {@code AuthService.register} et {@code UserService.updateProfile} quand le nom d'utilisateur appartient à un autre compte ; traduite en 409. À l'inscription, l'email est vérifié avant : si les deux sont pris, c'est {@link EmailAlreadyUsedException} qui est levée.
 */
public class UsernameAlreadyUsedException extends RuntimeException {
    public UsernameAlreadyUsedException(String message) {
        super(message);
    }
}
