package com.openclassrooms.mddapi.parcours;

import com.jayway.jsonpath.JsonPath;
import com.openclassrooms.mddapi.AbstractContainerIT;
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

    /** Corps JSON de {@code POST /api/auth/register}. */
    protected static String corpsInscription(String username, String email, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }
}