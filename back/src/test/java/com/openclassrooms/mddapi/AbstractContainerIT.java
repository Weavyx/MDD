package com.openclassrooms.mddapi;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

// Conteneur "singleton" partagé par toutes les sous-classes *IT : démarré une
// seule fois pour toute la JVM de test (jamais arrêté explicitement, Ryuk s'en charge en
// fin de run). Nécessaire car le champ @Container géré par @Testcontainers est un champ
// statique hérité unique : l'extension JUnit l'arrête après la première classe de test
// exécutée, ce qui casse les classes suivantes si on lui laisse gérer le cycle de vie.
public abstract class AbstractContainerIT {

    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    // Clé de signature des jetons pour les tests : 256 bits tirés au hasard, encodés en Base64
    // comme l'attend JwtService. Tirée une seule fois pour toute la JVM de test, pour que
    // l'application et les tests qui relisent mdd.jwt.secret (AuthentificationIT) voient la
    // même valeur. Les tests ne dépendent ainsi d'aucune variable JWT_SECRET.
    static final String JWT_SECRET_DE_TEST =
            Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());

    static {
        mysql.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @DynamicPropertySource
    static void jwtProperties(DynamicPropertyRegistry registry) {
        registry.add("mdd.jwt.secret", () -> JWT_SECRET_DE_TEST);
    }
}
