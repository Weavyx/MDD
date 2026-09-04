package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.service.TopicService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    @GetMapping
    public ResponseEntity<List<TopicResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(topicService.findAllWithSubscriptionStatus(userId));
    }

    @PostMapping("/{id}/subscribe")
    public ResponseEntity<Void> subscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long userId = Long.valueOf(jwt.getSubject());
        topicService.subscribe(userId, id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/subscribe")
    public ResponseEntity<Void> unsubscribe(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Long userId = Long.valueOf(jwt.getSubject());
        topicService.unsubscribe(userId, id);
        return ResponseEntity.noContent().build();
    }
}
