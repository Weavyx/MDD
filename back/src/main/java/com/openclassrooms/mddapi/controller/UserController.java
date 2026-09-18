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

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    @PostMapping("/me/subscriptions/{topicId}")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long topicId) {
        Long userId = Long.valueOf(jwt.getSubject());
        userService.subscribe(userId, topicId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/subscriptions/{topicId}")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long topicId) {
        Long userId = Long.valueOf(jwt.getSubject());
        userService.unsubscribe(userId, topicId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/feed")
    public ResponseEntity<List<PostSummaryResponse>> findFeed(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "asc|desc") String sort) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(postService.findFeed(userId, Sort.Direction.fromString(sort)));
    }
}
