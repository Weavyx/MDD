package com.openclassrooms.mddapi.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Tests unitaires de {@link JwtService}, sans Spring ni mock : une clé HS256 aléatoire est
 * générée pour chaque test, et les jetons émis sont relus avec la même clé.
 */
class JwtServiceTest {

    private static final String ISSUER = "mdd-api";
    private static final long EXPIRATION_MINUTES = 60L;

    private final String secret = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
    private final JwtService jwtService = new JwtService(secret, ISSUER, EXPIRATION_MINUTES);

    @Test
    void generateToken_puisExtractUserId_retrouveLId() {
        // Arrange
        String jeton = jwtService.generateToken("42");

        // Act
        Long userId = jwtService.extractUserId(jeton);

        // Assert
        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void generateToken_porteEmetteurDatesEtAlgorithmeHS256() {
        // Arrange
        Instant avant = Instant.now();

        // Act
        String jeton = jwtService.generateToken("42");

        // Assert
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .build()
                .parseSignedClaims(jeton);
        Claims claims = jws.getPayload();
        assertThat(jws.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration().toInstant())
                .isCloseTo(avant.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES), within(2, ChronoUnit.SECONDS));
    }

    @Test
    void constructeur_cleDeMoinsDe256Bits_refusee() {
        // Arrange
        String cleDe128Bits = Encoders.BASE64.encode(new byte[16]);

        // Act + Assert
        assertThatThrownBy(() -> new JwtService(cleDe128Bits, ISSUER, EXPIRATION_MINUTES))
                .isInstanceOf(WeakKeyException.class);
    }
}
