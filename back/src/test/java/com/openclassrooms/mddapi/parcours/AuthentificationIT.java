package com.openclassrooms.mddapi.parcours;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Transversal : toutes les routes hors inscription et connexion exigent un JWT valide.
 * Testé une fois ici plutôt que dans chaque fichier d'action, puisque c'est le même filtre
 * de sécurité pour toutes les routes.
 */
class AuthentificationIT extends ParcoursIT {

    /** Encodeur de l'application : même clé que le décodeur testé. */
    @Autowired
    private JwtEncoder jwtEncoder;

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
        byte[] autreCle = new byte[32];
        new SecureRandom().nextBytes(autreCle);
        JwtEncoder autreEncodeur = new NimbusJwtEncoder(new ImmutableSecret<>(autreCle));
        Instant maintenant = Instant.now();
        String jeton = encoder(autreEncodeur, maintenant, maintenant.plus(1, ChronoUnit.HOURS));

        // Act
        ResultActions reponse = mockMvc.perform(get("/api/user")
                .header("Authorization", "Bearer " + jeton));

        // Assert : « invalid_token » signifie que le jeton a été lu puis refusé, et non qu'il manque
        reponse.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    @Test
    void routeProtegee_jetonExpire_retourne401() throws Exception {
        // Arrange : bonne clé, expiration une heure dans le passé (au-delà de la tolérance de 60 s)
        Instant maintenant = Instant.now();
        String jeton = encoder(jwtEncoder, maintenant.minus(2, ChronoUnit.HOURS), maintenant.minus(1, ChronoUnit.HOURS));

        // Act
        ResultActions reponse = mockMvc.perform(get("/api/user")
                .header("Authorization", "Bearer " + jeton));

        // Assert
        reponse.andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    /** Fabrique un JWT HS256 avec l'encodeur donné et les dates d'émission et d'expiration voulues. */
    private String encoder(JwtEncoder encodeur, Instant emisLe, Instant expireLe) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("mdd-api")
                .issuedAt(emisLe)
                .expiresAt(expireLe)
                .subject("1")
                .build();
        JwsHeader entete = JwsHeader.with(MacAlgorithm.HS256).build();
        return encodeur.encode(JwtEncoderParameters.from(entete, claims)).getTokenValue();
    }
}
