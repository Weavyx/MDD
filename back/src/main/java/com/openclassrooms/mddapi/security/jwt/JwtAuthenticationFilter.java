package com.openclassrooms.mddapi.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authentifie une requête à partir de l'en-tête {@code Authorization: Bearer <jeton>}.
 * <p>
 * Jeton valide : l'id de l'utilisateur ({@code sub}) devient le principal d'une
 * authentification déjà vérifiée, sans mot de passe ni rôle, que les contrôleurs reçoivent
 * par {@code @AuthenticationPrincipal Long userId}. Aucune requête en base : la signature du
 * jeton suffit.
 * <p>
 * En-tête absent, jeton mal formé, mal signé ou expiré : le filtre n'écrit aucune réponse et
 * laisse la requête continuer sans authentification. Sur une route protégée, Spring Security
 * appelle alors le point d'entrée de {@code SecurityConfig}, qui répond 401.
 * <p>
 * L'authentification est aussi enregistrée dans les attributs de la requête : sans cela, la
 * réexpédition interne vers {@code /error} (erreur rendue par le conteneur) repartirait sans
 * authentification et répondrait 401 au lieu de l'erreur réelle.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                Long userId = jwtService.extractUserId(header.substring(BEARER_PREFIX.length()));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(userId, null, List.of()));
                SecurityContextHolder.setContext(context);
                securityContextRepository.saveContext(context, request, response);
            } catch (ExpiredJwtException e) {
                logger.info("Jeton JWT expiré");
            } catch (JwtException | IllegalArgumentException e) {
                logger.warn("Jeton JWT refusé : " + e.getClass().getSimpleName());
            }
        }
        filterChain.doFilter(request, response);
    }
}
