package com.openclassrooms.mddapi.parcours;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transversal : toutes les routes hors inscription et connexion exigent un JWT valide.
 * Testé une fois ici plutôt que dans chaque fichier d'action, puisque c'est le même filtre
 * de sécurité pour toutes les routes.
 * <p>
 * Un jeton refusé reçoit la même réponse qu'un jeton absent (401, {@code WWW-Authenticate: Bearer}) :
 * ce qui prouve que le jeton est lu puis refusé, et non ignoré, ce sont les mutations du filtre
 * et de {@code JwtService} (voir {@code RAPPORT_DE_TESTS.md}, §9).
 */
class AuthentificationIT extends ParcoursIT {

    /** Clé de l'application, pour signer un jeton authentique mais expiré. */
    @Value("${mdd.jwt.secret}")
    private String secretDeLApplication;

    @ParameterizedTest(name = "{0} {1} sans jeton")
    @CsvSource({
            "GET, /api/topics",
            "GET, /api/user",
            "PATCH, /api/user",
            "POST, /api/user/subscriptions/1",
            "DELETE, /api/user/subscriptions/1",
            "GET, /api/user/feed",
            "POST, /api/posts",
            "GET, /api/posts/1",
            "POST, /api/posts/1/comments"
    })
    void routeProtegee_sansJeton_retourne401(String methode, String route) throws Exception {
        // Arrange : aucune en-tête Authorization

        // Act
        ResultActions reponse = mockMvc.perform(request(HttpMethod.valueOf(methode), route));

        // Assert
        reponse.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void routeProtegee_jetonSigneAvecUneAutreCle_retourne401() throws Exception {
        // Arrange : jeton bien formé et non expiré, mais signé avec une clé inconnue de l'API
        SecretKey autreCle = Jwts.SIG.HS256.key().build();
        Instant maintenant = Instant.now();
        String jeton = signer(autreCle, maintenant, maintenant.plus(1, ChronoUnit.HOURS));

        // Act
        ResultActions reponse = mockMvc.perform(get("/api/user")
                .header("Authorization", "Bearer " + jeton));

        // Assert
        reponse.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    @Test
    void routeProtegee_jetonExpire_retourne401() throws Exception {
        // Arrange : clé de l'application, expiration une heure dans le passé
        SecretKey cleDeLApplication = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretDeLApplication));
        Instant maintenant = Instant.now();
        String jeton = signer(cleDeLApplication, maintenant.minus(2, ChronoUnit.HOURS), maintenant.minus(1, ChronoUnit.HOURS));

        // Act
        ResultActions reponse = mockMvc.perform(get("/api/user")
                .header("Authorization", "Bearer " + jeton));

        // Assert
        reponse.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")));
    }

    /** Fabrique un JWT HS256 signé avec {@code cle}, avec les dates d'émission et d'expiration voulues. */
    private String signer(SecretKey cle, Instant emisLe, Instant expireLe) {
        return Jwts.builder()
                .issuer("mdd-api")
                .issuedAt(Date.from(emisLe))
                .expiration(Date.from(expireLe))
                .subject("1")
                .signWith(cle, Jwts.SIG.HS256)
                .compact();
    }
}
