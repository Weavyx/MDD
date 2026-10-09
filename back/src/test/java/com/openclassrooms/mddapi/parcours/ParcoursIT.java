package com.openclassrooms.mddapi.parcours;

import com.jayway.jsonpath.JsonPath;
import com.openclassrooms.mddapi.AbstractContainerIT;
import com.openclassrooms.mddapi.repository.TopicRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base des tests d'intégration par action de la spécification : toute l'application
 * (filtre de sécurité, contrôleur, service, repository) contre MySQL (Testcontainers),
 * sans aucune doublure.
 * <p>
 * Chaque test suit Arrange–Act–Assert avec une seule action (Act). Les données de
 * l'Arrange passent par les vraies routes HTTP ; seuls les thèmes viennent de la
 * migration Flyway V2.
 * <p>
 * {@code @Transactional} annule chaque test à la fin (isolation). Limite : MockMvc
 * s'exécute dans le thread du test, donc les services rejoignent sa transaction et
 * aucun commit réel n'est exercé. Pour que l'Assert relise la base et non le cache
 * d'Hibernate, les tests appellent {@link #viderLeCache()} juste avant de relire la
 * base.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class ParcoursIT extends AbstractContainerIT {

    protected static final String MOT_DE_PASSE = "Password1!";

    @Autowired
    protected MockMvc mockMvc;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected TopicRepository topicRepository;

    /** Écrit en base les modifications en attente, puis vide le cache d'Hibernate. */
    protected void viderLeCache() {
        entityManager.flush();
        entityManager.clear();
    }

    /** Arrange : inscrit {@code username} (e-mail {@code username@mail.com}) par la vraie route. */
    protected void inscrire(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpsInscription(username, username + "@mail.com", MOT_DE_PASSE)))
                .andExpect(status().isCreated());
    }

    /** Arrange : se connecte par la vraie route et renvoie le jeton. */
    protected String connecter(String identifiant) throws Exception {
        MvcResult connexion = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + identifiant + "\",\"password\":\"" + MOT_DE_PASSE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(connexion.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }

    /** Arrange : inscription puis connexion par e-mail ; renvoie le jeton. */
    protected String inscrireEtConnecter(String username) throws Exception {
        inscrire(username);
        return connecter(username + "@mail.com");
    }

    /** Arrange : id d'un thème de référence (migration Flyway V2) d'après son nom. */
    protected Long idDuTheme(String nom) {
        return topicRepository.findAll().stream()
                .filter(theme -> theme.getName().equals(nom))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    /** Arrange : abonne le porteur de {@code jeton} au thème {@code idTheme} par la vraie route. */
    protected void abonner(String jeton, Long idTheme) throws Exception {
        mockMvc.perform(post("/api/user/subscriptions/{id}", idTheme)
                        .header("Authorization", "Bearer " + jeton))
                .andExpect(status().isCreated());
    }

    /** Corps JSON de {@code POST /api/auth/register}. */
    protected static String corpsInscription(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}