package com.openclassrooms.mddapi;

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

    static {
        mysql.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }
}
