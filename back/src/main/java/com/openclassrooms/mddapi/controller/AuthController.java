package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.AuthResponse;
import com.openclassrooms.mddapi.dto.LoginRequest;
import com.openclassrooms.mddapi.dto.RegisterRequest;
import com.openclassrooms.mddapi.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Les deux seules routes publiques de l'API ({@code SecurityConfig}). Pas de
 * {@code /logout} (jeton stateless, supprimé côté client) ni de {@code /me}
 * (retiré au profit de {@code GET /api/users/me}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 201 avec le jeton (l'inscription connecte) ; 400 {@code fieldErrors} si le corps est
     * invalide ; 409 si l'email, ou sinon le nom d'utilisateur, est déjà pris.
     * Un corps sans champ {@code password} passe la validation (pas de {@code @NotBlank})
     * et aboutit à un 500 — anomalie consignée dans la revue technique, axe p.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 200 avec le jeton ; 400 {@code fieldErrors} si un champ manque ; 401 à corps vide
     * (hors {@code ErrorResponse}, rendu par Spring Security) si l'identifiant est inconnu
     * ou le mot de passe faux, sans distinction entre les deux.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}