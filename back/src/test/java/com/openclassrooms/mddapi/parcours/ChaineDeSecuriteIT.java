package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.AbstractContainerIT;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base des tests d'intégration qui passent par la chaîne de sécurité : inscription, connexion et
 * filtre JWT ({@code InscriptionIT}, {@code ConnexionIT}, {@link AuthentificationIT}). Toute
 * l'application (filtres, contrôleur, service, repository) contre MySQL (Testcontainers), sans
 * aucune doublure. Les tests des autres actions étendent {@link ActionIT}, sans filtres.
 * <p>
 * Seule l'action testée passe par sa route : les comptes nécessaires sont créés par le
 * repository, avec l'encodeur de mots de passe de l'application ({@link #creerCompte}).
 * <p>
 * {@code @Transactional} annule chaque test à la fin. MockMvc s'exécute dans le thread du test :
 * les services rejoignent sa transaction et aucun commit réel n'est exercé. Pour que l'Assert
 * relise la base et non le cache d'Hibernate, les tests appellent {@link #viderLeCache()} juste
 * avant de relire la base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ChaineDeSecuriteIT extends AbstractContainerIT {

    protected static final String MOT_DE_PASSE = "Password1!";

    @Autowired
    protected MockMvc mockMvc;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** Écrit en base les modifications en attente, puis vide le cache d'Hibernate. */
    protected void viderLeCache() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Arrange : enregistre le compte {@code username} (e-mail {@code username@mail.com}) par le
     * repository, son mot de passe haché par l'encodeur de l'application, comme à l'inscription.
     */
    protected User creerCompte(String username, String motDePasse) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@mail.com");
        user.setPasswordHash(passwordEncoder.encode(motDePasse));
        return userRepository.save(user);
    }

    /** Corps JSON de {@code POST /api/auth/register}. */
    protected static String corpsInscription(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}
