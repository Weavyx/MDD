package com.openclassrooms.mddapi.security;

import com.jayway.jsonpath.JsonPath;
import com.openclassrooms.mddapi.AbstractContainerIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parcours utilisateur de bout en bout : requête HTTP, filtre de sécurité, contrôleur,
 * service, repository et MySQL (Testcontainers).
 * <p>
 * Ce qu'ils prouvent : aucune classe de l'application n'est remplacée par une doublure,
 * et le jeton est obtenu par la vraie route {@code POST /api/auth/login} puis envoyé en
 * {@code Authorization: Bearer}, donc validé par le vrai {@code JwtDecoder}. Toutes les
 * données passent par les routes HTTP ; seuls les topics viennent de la migration Flyway V2.
 * <p>
 * Leur limite : le serveur HTTP est simulé par MockMvc (ni Tomcat ni socket). Avec
 * {@code @Transactional}, MockMvc s'exécute dans le thread du test : les services
 * rejoignent la transaction du test au lieu d'ouvrir la leur, et tout est annulé à la fin.
 * Les commits et les frontières de transaction réelles ne sont donc pas exercés.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParcoursUtilisateurIT extends AbstractContainerIT {

    private static final String PASSWORD = "Password1!";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void inscriptionPuisConnexion_memesIdentifiants_retourneUnJetonQuiOuvreLeProfil() throws Exception {
        // Arrange
        String username = "parcours-inscription";
        String email = "parcours-inscription@mail.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());

        // Act
        ResultActions connexion = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"));

        // Assert
        connexion.andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())));
        // Jeton de la connexion, et non celui que renvoie aussi l'inscription.
        String token = JsonPath.read(connexion.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
        mockMvc.perform(get("/api/user").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void listeDesThemes_jetonReel_retourneLesThemesDeReference() throws Exception {
        // Arrange
        String token = inscrireEtConnecter("parcours-themes");

        // Act
        ResultActions themes = mockMvc.perform(get("/api/topics").header("Authorization", "Bearer " + token));

        // Assert : "Java" est copié de V2__insert_reference_topics.sql.
        themes.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Java")));
    }

    @Test
    void creationArticle_puisLectureEtCommentaire_retourneLArticleAvecSonCommentaire() throws Exception {
        // Arrange
        String username = "parcours-article";
        String token = inscrireEtConnecter(username);
        MvcResult themes = mockMvc.perform(get("/api/topics").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> idsJava = JsonPath.read(themes.getResponse().getContentAsString(StandardCharsets.UTF_8), "$[?(@.name == 'Java')].id");
        long topicId = idsJava.get(0).longValue();

        // Act : création puis lecture de l'article
        MvcResult creation = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"title\":\"Titre du parcours\",\"content\":\"Contenu du parcours\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String location = creation.getResponse().getHeader("Location");
        long postId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
        ResultActions lecture = mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + token));

        // Assert
        lecture.andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre du parcours"))
                .andExpect(jsonPath("$.content").value("Contenu du parcours"))
                .andExpect(jsonPath("$.topicName").value("Java"))
                .andExpect(jsonPath("$.author").value(username));

        // Act : commentaire puis relecture (les commentaires n'ont pas de route propre)
        mockMvc.perform(post("/api/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Commentaire du parcours\"}"))
                .andExpect(status().isCreated());
        ResultActions relecture = mockMvc.perform(get("/api/posts/" + postId).header("Authorization", "Bearer " + token));

        // Assert
        relecture.andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(1)))
                .andExpect(jsonPath("$.comments[0].content").value("Commentaire du parcours"))
                .andExpect(jsonPath("$.comments[0].author").value(username));
    }

    @Test
    void abonnement_puisFilPuisDesabonnement_lArticleApparaitPuisDisparait() throws Exception {
        // Arrange : A publie sur le topic "Spring", B n'y est pas encore abonné.
        String tokenAuteur = inscrireEtConnecter("parcours-auteur");
        String tokenAbonne = inscrireEtConnecter("parcours-abonne");
        MvcResult themes = mockMvc.perform(get("/api/topics").header("Authorization", "Bearer " + tokenAbonne))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> idsSpring = JsonPath.read(themes.getResponse().getContentAsString(StandardCharsets.UTF_8), "$[?(@.name == 'Spring')].id");
        long topicId = idsSpring.get(0).longValue();
        String titre = "Article du parcours abonnement";
        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + tokenAuteur)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":" + topicId + ",\"title\":\"" + titre + "\",\"content\":\"Contenu de A\"}"))
                .andExpect(status().isCreated());

        // Act : B s'abonne puis lit son fil
        mockMvc.perform(post("/api/user/subscriptions/" + topicId).header("Authorization", "Bearer " + tokenAbonne))
                .andExpect(status().isCreated());
        ResultActions filAbonne = mockMvc.perform(get("/api/user/feed").header("Authorization", "Bearer " + tokenAbonne));

        // Assert
        filAbonne.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", hasItem(titre)));

        // Act : B se désabonne puis relit son fil
        mockMvc.perform(delete("/api/user/subscriptions/" + topicId).header("Authorization", "Bearer " + tokenAbonne))
                .andExpect(status().isNoContent());
        ResultActions filDesabonne = mockMvc.perform(get("/api/user/feed").header("Authorization", "Bearer " + tokenAbonne));

        // Assert
        filDesabonne.andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", not(hasItem(titre))));
    }

    /** Inscription puis connexion par les vraies routes ; renvoie le jeton de la connexion. */
    private String inscrireEtConnecter(String username) throws Exception {
        String email = username + "@mail.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
        MvcResult connexion = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(connexion.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.token");
    }
}
