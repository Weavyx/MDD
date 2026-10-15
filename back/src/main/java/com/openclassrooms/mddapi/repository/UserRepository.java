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
     * {@code identifier}, comparé selon la collation de la table
     * ({@code utf8mb4_0900_ai_ci} : insensible à la casse et aux accents). Les deux
     * colonnes étant uniques, le résultat est au plus une ligne : un nom d'utilisateur ne
     * peut pas contenir {@code @} ({@code @Pattern} de {@code RegisterRequest} et de
     * {@code UpdateProfileRequest}), alors qu'un email valide en contient un
     * ({@code @Email}) ; aucun nom ne peut donc égaler l'email d'un autre compte. Seule une
     * écriture directe en base, hors de l'API, créerait ce cas, et la requête échouerait
     * alors avec plus d'un résultat.
     */
    @Query("SELECT u FROM User u WHERE u.email = :identifier OR u.username = :identifier")
    Optional<User> findByEmailOrUsername(@Param("identifier") String identifier);

    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmailAndIdNot(String email, Long id);
    boolean existsByUsernameAndIdNot(String username, Long id);
}
