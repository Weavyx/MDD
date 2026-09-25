package com.openclassrooms.mddapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Chaîne de sécurité de l'API : serveur de ressources OAuth2 en mode JWT, sans session.
 * <p>
 * Ce que cette configuration fixe et qui ne se lit pas dans le code des contrôleurs :
 * <ul>
 *   <li>seules {@code POST /api/auth/register} et {@code POST /api/auth/login} sont
 *       publiques ; toute autre route, y compris inexistante, exige un jeton valide ;</li>
 *   <li>aucune autorité ni rôle n'est dérivé du jeton : il n'existe qu'un niveau
 *       d'accès, « authentifié », et donc jamais de 403 ;</li>
 *   <li>le principal injecté par {@code @AuthenticationPrincipal} est le
 *       {@code org.springframework.security.oauth2.jwt.Jwt} brut, pas un
 *       {@code UserDetails} — {@code UserDetailsImpl} ne sert qu'au login ;</li>
 *   <li>les 401 (jeton absent, invalide, expiré, ou identifiants faux au login) sont rendus
 *       par le {@code BearerTokenAuthenticationEntryPoint} par défaut : corps vide et
 *       en-tête {@code WWW-Authenticate}, hors du format {@code ErrorResponse}. Aucun
 *       point d'entrée personnalisé n'est installé, par choix documenté ;</li>
 *   <li>CSRF désactivé parce qu'aucun cookie n'est utilisé ; CORS limité aux origines de
 *       {@code mdd.cors.allowed-origins} (séparées par des virgules), aux cinq méthodes de
 *       l'API et aux en-têtes {@code Authorization} et {@code Content-Type}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Value("${mdd.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated());

        return http.build();
    }

    /**
     * Expose le gestionnaire d'authentification par mot de passe, utilisé uniquement par
     * {@code AuthService.login} ; il s'appuie sur {@code UserDetailsServiceImpl} et le
     * {@code PasswordEncoder} BCrypt. Les requêtes porteuses d'un JWT ne passent pas par lui.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}