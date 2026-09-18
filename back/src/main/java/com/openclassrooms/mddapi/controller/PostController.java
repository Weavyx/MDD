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

    @PostMapping
    public ResponseEntity<Void> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePostRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        Long postId = postService.create(userId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(postId).toUri();
        return ResponseEntity.created(location).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDetailResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.findById(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<Void> addComment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody CreateCommentRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        postService.addComment(userId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
