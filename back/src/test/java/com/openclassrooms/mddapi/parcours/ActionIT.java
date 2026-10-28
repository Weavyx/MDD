package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.AbstractContainerIT;
import com.openclassrooms.mddapi.model.Comment;
import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Subscription;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.CommentRepository;
import com.openclassrooms.mddapi.repository.PostRepository;
import com.openclassrooms.mddapi.repository.SubscriptionRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Base des tests d'intégration d'une action de la spécification : contrôleur, service,
 * repository et MySQL (Testcontainers) réels, sans aucune doublure.
 * <p>
 * Les filtres ne sont pas exécutés ({@code addFilters = false}) : l'Arrange crée ses données
 * par les repositories et pose lui-même l'utilisateur connecté, avec le même objet que
 * {@code JwtAuthenticationFilter} (principal = id numérique). Seul l'Act est une requête HTTP.
 * L'inscription, la connexion et la chaîne de sécurité sont testées à part
 * ({@code InscriptionIT}, {@code ConnexionIT}, {@link AuthentificationIT}).
 * <p>
 * {@code @Transactional} annule chaque test à la fin. MockMvc s'exécute dans le thread du test :
 * les services rejoignent sa transaction et aucun commit réel n'est exercé. Pour que l'Assert
 * relise la base et non le cache d'Hibernate, les tests appellent {@link #viderLeCache()} juste
 * avant de relire la base.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
abstract class ActionIT extends AbstractContainerIT {

    /** Valeur de {@code password_hash} : jamais lue, aucun test d'action ne se connecte. */
    private static final String HACHAGE_NON_UTILISE = "non-utilise-sans-connexion";

    @Autowired
    protected MockMvc mockMvc;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TopicRepository topicRepository;

    @Autowired
    protected SubscriptionRepository subscriptionRepository;

    @Autowired
    protected PostRepository postRepository;

    @Autowired
    protected CommentRepository commentRepository;

    /** Aucun test ne démarre avec l'utilisateur connecté d'un test précédent. */
    @BeforeEach
    void partirSansUtilisateurConnecte() {
        SecurityContextHolder.clearContext();
    }

    /** Aucun test ne laisse son utilisateur connecté au test suivant, quelle que soit sa classe. */
    @AfterEach
    void neRienLaisserAuTestSuivant() {
        SecurityContextHolder.clearContext();
    }

    /** Écrit en base les modifications en attente, puis vide le cache d'Hibernate. */
    protected void viderLeCache() {
        entityManager.flush();
        entityManager.clear();
    }

    /** Arrange : enregistre le compte {@code username} (e-mail {@code username@mail.com}) par le repository. */
    protected User creerUtilisateur(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@mail.com");
        user.setPasswordHash(HACHAGE_NON_UTILISE);
        return userRepository.save(user);
    }

    /**
     * Arrange : pose {@code user} comme utilisateur connecté, avec l'objet que construit
     * {@code JwtAuthenticationFilter} pour un jeton valide. Aucune route de connexion n'est appelée.
     */
    protected void connecterEnTantQue(User user) {
        connecterEnTantQue(user.getId());
    }

    /**
     * Arrange : pose l'id {@code idUtilisateur} comme utilisateur connecté, même si aucun compte
     * ne le porte (cas d'un jeton valide dont le compte n'existe plus).
     */
    protected void connecterEnTantQue(Long idUtilisateur) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(idUtilisateur, null, List.of()));
    }

    /** Arrange : id d'un thème de référence (migration Flyway V2) d'après son nom. */
    protected Long idDuTheme(String nom) {
        return topicRepository.findAll().stream()
                .filter(theme -> theme.getName().equals(nom))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** Arrange : abonne {@code user} au thème {@code idTheme} par le repository. */
    protected void abonner(User user, Long idTheme) {
        subscriptionRepository.save(new Subscription(user, topicRepository.findById(idTheme).orElseThrow()));
    }

    /** Arrange : publie l'article de {@code auteur} dans le thème {@code idTheme} par le repository. */
    protected Post creerArticle(User auteur, Long idTheme, String titre, String contenu) {
        return postRepository.save(new Post(titre, contenu, auteur, topicRepository.findById(idTheme).orElseThrow()));
    }

    /** Arrange : enregistre le commentaire de {@code auteur} sur {@code article} par le repository. */
    protected Comment commenter(User auteur, Post article, String contenu) {
        return commentRepository.save(new Comment(auteur, article, contenu));
    }
}
