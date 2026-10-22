package com.openclassrooms.mddapi.parcours;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nombre de requêtes SQL des trois lectures principales (fil, détail d'un article, profil) :
 * preuve que le chargement anticipé ({@code @EntityGraph}) évite le N+1.
 * <p>
 * Classe à part, comme {@code AuthentificationIT} pour les 401 communs à toutes les routes :
 * la propriété vérifiée est commune aux lectures, et les statistiques d'Hibernate ne sont
 * activées que pour cette classe, sans changer la configuration des autres tests de parcours.
 * <p>
 * Les tests de consultation ne peuvent pas voir ce chargement : dans leur transaction ouverte,
 * un chargement paresseux réussit aussi, avec une requête de plus par auteur, thème ou auteur
 * de commentaire. Le nombre attendu est exact : toute requête ajoutée à l'une de ces lectures
 * fait échouer le test, volontairement.
 * <p>
 * {@link #viderLeCache()} est appelé en fin d'Arrange, puis les statistiques sont remises à
 * zéro : seules les requêtes de l'Act sont comptées, et aucune n'est servie par le cache.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class NombreDeRequetesIT extends ParcoursIT {

    /** Statistiques d'Hibernate de l'application. */
    private Statistics statistiques() {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    /** Fin d'Arrange : écrit et vide le cache, puis remet le compteur de requêtes à zéro. */
    private void demarrerLeComptage() {
        viderLeCache();
        statistiques().clear();
    }

    /** Act commun : lecture authentifiée de {@code url}. */
    private ResultActions lire(String jeton, String url) throws Exception {
        return mockMvc.perform(get(url).header("Authorization", "Bearer " + jeton));
    }

    @Test
    void fil_chargeAuteursEtThemesEnUneRequete() throws Exception {
        // Arrange : trois articles de deux auteurs dans les deux thèmes suivis
        String jetonLecteur = inscrireEtConnecter("requetes-fil-lecteur");
        abonner(jetonLecteur, idDuTheme("Java"));
        abonner(jetonLecteur, idDuTheme("Python"));
        String jetonAuteur1 = inscrireEtConnecter("requetes-fil-auteur1");
        String jetonAuteur2 = inscrireEtConnecter("requetes-fil-auteur2");
        creerArticle(jetonAuteur1, idDuTheme("Java"), "Requetes Java 1", "Contenu");
        creerArticle(jetonAuteur1, idDuTheme("Python"), "Requetes Python", "Contenu");
        creerArticle(jetonAuteur2, idDuTheme("Java"), "Requetes Java 2", "Contenu");
        demarrerLeComptage();

        // Act
        ResultActions reponse = lire(jetonLecteur, "/api/user/feed");

        // Assert : la taille garantit que le compte porte sur les trois articles
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        assertThat(statistiques().getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void detailDUnArticle_chargeArticleEtCommentairesEnDeuxRequetes() throws Exception {
        // Arrange : un article et trois commentaires de trois autres comptes
        String jetonAuteur = inscrireEtConnecter("requetes-detail-auteur");
        Long idArticle = creerArticle(jetonAuteur, idDuTheme("Java"), "Requetes detail", "Contenu");
        commenter(inscrireEtConnecter("requetes-detail-c1"), idArticle, "Commentaire 1");
        commenter(inscrireEtConnecter("requetes-detail-c2"), idArticle, "Commentaire 2");
        commenter(inscrireEtConnecter("requetes-detail-c3"), idArticle, "Commentaire 3");
        demarrerLeComptage();

        // Act
        ResultActions reponse = lire(jetonAuteur, "/api/posts/" + idArticle);

        // Assert : article avec auteur et thème, puis commentaires avec leur auteur
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.comments", hasSize(3)));
        assertThat(statistiques().getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void profil_chargeCompteEtAbonnementsEnDeuxRequetes() throws Exception {
        // Arrange : trois abonnements
        String jeton = inscrireEtConnecter("requetes-profil");
        abonner(jeton, idDuTheme("Java"));
        abonner(jeton, idDuTheme("Python"));
        abonner(jeton, idDuTheme("Angular"));
        demarrerLeComptage();

        // Act
        ResultActions reponse = lire(jeton, "/api/user");

        // Assert : compte, puis abonnements avec leur thème
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.subscriptions", hasSize(3)));
        assertThat(statistiques().getPrepareStatementCount()).isEqualTo(2);
    }
}
