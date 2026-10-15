package com.openclassrooms.mddapi.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Émission et validation des jetons d'accès, avec la bibliothèque jjwt et une clé HMAC unique.
 * <p>
 * La clé est {@code mdd.jwt.secret}, attendue en Base64 ; elle n'est pas définie dans
 * {@code application.properties} mais dans le profil {@code local} (fichier gitignoré) via
 * la variable d'environnement {@code JWT_SECRET}. {@code Keys.hmacShaKeyFor} refuse une clé
 * décodée de moins de 256 bits : l'application ne démarre pas avec une clé trop courte pour HS256.
 * <p>
 * La validation vérifie la signature et l'expiration ({@code exp}) : le claim {@code iss}
 * n'est pas contrôlé, et aucune révocation n'existe — un jeton reste valable jusqu'à son
 * {@code exp}.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final String jwtIssuer;
    private final long jwtExpirationMinutes;

    public JwtService(@Value("${mdd.jwt.secret}") String jwtSecret,
                      @Value("${mdd.jwt.issuer}") String jwtIssuer,
                      @Value("${mdd.jwt.expiration-minutes}") long jwtExpirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.jwtIssuer = jwtIssuer;
        this.jwtExpirationMinutes = jwtExpirationMinutes;
    }

    /**
     * Signe un jeton HS256 dont les seuls claims sont {@code iss} ({@code mdd.jwt.issuer}),
     * {@code iat}, {@code exp} ({@code iat} + {@code mdd.jwt.expiration-minutes}) et {@code sub}.
     * <p>
     * {@code sub} doit être l'id numérique de l'utilisateur sous forme de chaîne :
     * {@link #extractUserId} le reconvertit en {@code Long}. Ni email, ni nom d'utilisateur,
     * ni rôle ne sont embarqués. Le paramètre n'est pas vérifié : ce service ne consulte
     * jamais la base.
     */
    public String generateToken(String userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(jwtIssuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtExpirationMinutes, ChronoUnit.MINUTES)))
                .subject(userId)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Vérifie la signature et l'expiration du jeton, puis renvoie l'id porté par {@code sub}.
     *
     * @throws JwtException             si le jeton est mal formé, mal signé ou expiré
     * @throws IllegalArgumentException si le jeton est vide, ou si {@code sub} n'est pas un nombre
     */
    public Long extractUserId(String token) {
        String subject = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.valueOf(subject);
    }
}
