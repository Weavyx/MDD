package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.CreateCommentRequest;
import com.openclassrooms.mddapi.dto.CreatePostRequest;
import com.openclassrooms.mddapi.dto.PostDetailResponse;
import com.openclassrooms.mddapi.service.PostService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 201 sans corps, avec {@code Location: /api/posts/{id}} ; 400 {@code fieldErrors} ;
     * 404 si {@code topicId} est inconnu. L'auteur et la date ne sont pas dans le corps :
     * ils viennent du jeton et de la base.
     */
    @PostMapping
    public ResponseEntity<Void> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePostRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        Long postId = postService.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(postId).toUri();
        return ResponseEntity.created(location).build();
    }

    /**
     * 200 détail avec commentaires (du plus ancien au plus récent), accessible à tout
     * utilisateur authentifié sans condition d'abonnement ; 404 inconnu ; 400 id non numérique.
     * Seul endpoint protégé qui ne lit pas le jeton.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostDetailResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.findById(id));
    }

    /**
     * 201 sans corps ni {@code Location} (les commentaires n'ont pas de route propre : ils
     * se relisent via {@code GET /api/posts/{id}}) ; 400 {@code fieldErrors} ; 404 article inconnu.
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<Void> addComment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CreateCommentRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        postService.addComment(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
