package com.openclassrooms.mddapi.repository;

import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class AbstractRepositoryIT {

    // Conteneur "singleton" partagé par toutes les sous-classes *RepositoryIT : démarré une
    // seule fois pour toute la JVM de test (jamais arrêté explicitement, Ryuk s'en charge en
    // fin de run). Nécessaire car le champ @Container géré par @Testcontainers est un champ
    // statique hérité unique : l'extension JUnit l'arrête après la première classe de test
    // exécutée, ce qui casse les classes suivantes si on lui laisse gérer le cycle de vie.
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
