package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Post;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    /**
     * Tous les articles publiés dans un topic auquel {@code userId} est abonné, quel qu'en
     * soit l'auteur — ses propres articles compris s'ils sont dans un topic suivi, exclus
     * sinon. Non paginé. Le tri est laissé au paramètre {@code sort} (propriété
     * {@code createdAt} dans les appels actuels). Auteur et topic sont chargés dans la même
     * requête par le graphe d'entités ; c'est le seul {@code @EntityGraph} du projet dont
     * l'effet n'est pas vérifié par un test {@code Hibernate.isInitialized}.
     */
    @EntityGraph(attributePaths = {"user", "topic"})
    @Query("SELECT p FROM Post p WHERE p.topic IN (SELECT s.topic FROM Subscription s WHERE s.user.id = :userId)")
    List<Post> findPostsByUserId(@Param("userId") Long userId, Sort sort);

    @EntityGraph(attributePaths = {"user", "topic"})
    Optional<Post> findWithUserAndTopicById(Long id);
}
