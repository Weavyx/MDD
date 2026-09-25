package com.openclassrooms.mddapi.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Émission des jetons d'accès ; la validation est entièrement déléguée au
 * {@code JwtDecoder} de {@link JwtConfig} et au filtre Bearer de Spring Security.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;

    @Value("${mdd.jwt.issuer}")
    private String jwtIssuer;

    @Value("${mdd.jwt.expiration-minutes}")
    private long jwtExpirationMinutes;

    public JwtService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * Signe un jeton HS256 dont les seuls claims sont {@code iss} ({@code mdd.jwt.issuer},
     * chaîne simple non-URL, valide au sens de la RFC 7519), {@code iat}, {@code exp}
     * ({@code iat} + {@code mdd.jwt.expiration-minutes}) et {@code sub}.
     * <p>
     * {@code sub} doit être l'id numérique de l'utilisateur sous forme de chaîne : les
     * contrôleurs le reconvertissent par {@code Long.valueOf(jwt.getSubject())} ; toute
     * autre valeur produirait une {@code NumberFormatException} à la première requête
     * authentifiée. Ni email, ni nom d'utilisateur, ni rôle ne sont embarqués. Le paramètre
     * n'est pas vérifié : ce service ne consulte jamais la base.
     */
    public String generateToken(String userId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtIssuer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(jwtExpirationMinutes, ChronoUnit.MINUTES))
                .subject(userId)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }
}
