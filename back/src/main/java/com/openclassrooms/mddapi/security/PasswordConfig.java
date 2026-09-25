package com.openclassrooms.mddapi.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Encodeur BCrypt avec le coût par défaut (10), précédé d'une normalisation NFC
 * ({@link NfcPasswordEncoder}). BCrypt refuse un mot de passe de plus de 72 octets
 * (exception à l'encodage) : c'est la raison de la borne {@code @MaxUtf8Bytes(72)}
 * sur {@code RegisterRequest.password} et {@code UpdateProfileRequest.password}.
 * {@code encode(null)} renvoie {@code null} sans exception (spring-security-crypto 7.1.0,
 * vérifié) : la non-nullité doit être garantie en amont par Bean Validation.
 */
@Configuration
public class PasswordConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new NfcPasswordEncoder(new BCryptPasswordEncoder());
    }
}
