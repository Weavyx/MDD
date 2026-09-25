package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * L'utilisateur dont l'email <em>ou</em> le nom d'utilisateur vaut exactement
     * {@code identifier} (comparaison selon la collation MySQL, insensible à la casse par
     * défaut). Les deux colonnes étant uniques, le résultat est au plus une ligne — sauf
     * si le nom d'un compte est égal à l'email d'un autre, cas que le modèle n'interdit pas
     * et qui ferait échouer la requête avec plus d'un résultat.
     */
    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.username = :identifier")
    Optional<User> findByEmailOrUsername(@Param("identifier") String identifier);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByUsernameAndIdNot(String username, Long id);
}
