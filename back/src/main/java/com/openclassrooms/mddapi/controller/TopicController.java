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

    /**
     * 200 avec tous les topics, chacun portant {@code subscribed} pour l'appelant. Reste sous
     * {@code /api/topics} et non {@code /users/me} : la liste est la même pour tout le monde,
     * seul l'attribut est personnalisé (ressource « décorée »). Aucun cas d'erreur métier ;
     * pas de création ni de modification de topic par l'API.
     */
    @GetMapping
    public ResponseEntity<List<TopicResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(topicService.findAllWithSubscriptionStatus(userId));
    }
}
