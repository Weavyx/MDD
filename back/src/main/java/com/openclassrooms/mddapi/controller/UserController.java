package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.PostSummaryResponse;
import com.openclassrooms.mddapi.dto.UpdateProfileRequest;
import com.openclassrooms.mddapi.dto.UserProfileResponse;
import com.openclassrooms.mddapi.dto.UserResponse;
import com.openclassrooms.mddapi.service.PostService;
import com.openclassrooms.mddapi.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ressources propres à l'utilisateur courant, toutes sous {@code /me} : profil,
 * abonnements et fil. Aucune route ne prend d'id d'utilisateur ; l'identité est
 * toujours {@code Long.valueOf(jwt.getSubject())}. Le fil est ici et non sous
 * {@code /api/posts} parce que son jeu de résultats dépend de l'appelant (ressource
 * « filtrée »), d'où la dépendance de ce contrôleur à {@code PostService}.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    /** 200 profil avec abonnements ; 404 si le compte du jeton n'existe plus (pas 401). */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /**
     * 200 avec le profil mis à jour (sans hash) ; 400 {@code fieldErrors} ; 404 compte
     * disparu ; 409 email ou nom pris par un autre compte. Le mot de passe est optionnel :
     * absent ou blanc, il est conservé. Le jeton reste valide après changement d'email
     * ou de nom, puisqu'il ne porte que l'id.
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    /** 200 sans corps (pas 201 : pas de ressource adressable créée) ; 404 topic inconnu ; 409 déjà abonné ; 400 topicId non numérique. */
    @PostMapping("/me/subscriptions/{topicId}")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long topicId) {
        Long userId = Long.valueOf(jwt.getSubject());
        userService.subscribe(userId, topicId);
        return ResponseEntity.ok().build();
    }

    /** 204 même sans abonnement préalable (idempotent) ; 404 seulement si le topic n'existe pas ; 400 topicId non numérique. */
    @DeleteMapping("/me/subscriptions/{topicId}")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long topicId) {
        Long userId = Long.valueOf(jwt.getSubject());
        userService.unsubscribe(userId, topicId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 200 liste non paginée des résumés (extrait ≤ 200 caractères), vide si aucun abonnement ;
     * {@code sort} vaut {@code asc} ou {@code desc} exactement (minuscules), défaut {@code desc},
     * toute autre valeur → 400 sans {@code fieldErrors}.
     */
    @GetMapping("/me/feed")
    public ResponseEntity<List<PostSummaryResponse>> findFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String sort) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(postService.findFeed(userId, Sort.Direction.fromString(sort)));
    }
}
