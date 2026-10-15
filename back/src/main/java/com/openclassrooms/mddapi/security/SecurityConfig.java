package com.openclassrooms.mddapi.security;

import com.openclassrooms.mddapi.security.jwt.JwtAuthenticationFilter;
import com.openclassrooms.mddapi.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Chaîne de sécurité de l'API : authentification par JWT (bibliothèque jjwt) avec un filtre
 * maison, sans session.
 * <p>
 * Ce que cette configuration fixe et qui ne se lit pas dans le code des contrôleurs :
 * <ul>
 *   <li>seules {@code POST /api/auth/register} et {@code POST /api/auth/login} sont
 *       publiques ; toute autre route, y compris inexistante, exige un jeton valide ;</li>
 *   <li>{@link JwtAuthenticationFilter} lit l'en-tête {@code Authorization: Bearer}, fait
 *       valider le jeton par {@link JwtService} et place l'id de l'utilisateur ({@code Long})
 *       comme principal : c'est ce que reçoit {@code @AuthenticationPrincipal} ;</li>
 *   <li>aucune autorité ni rôle n'est dérivé du jeton : il n'existe qu'un niveau
 *       d'accès, « authentifié », et donc jamais de 403 ;</li>
 *   <li>tous les 401 (jeton absent, invalide, expiré, ou identifiants faux au login) sont
 *       rendus par le point d'entrée déclaré ici : corps vide et en-tête
 *       {@code WWW-Authenticate: Bearer} (obligatoire sur un 401, RFC 9110 §15.5.2), hors
 *       du format {@code ErrorResponse} ;</li>
 *   <li>CSRF désactivé parce qu'aucun cookie n'est utilisé ; CORS limité aux origines de
 *       {@code mdd.cors.allowed-origins} (séparées par des virgules, espaces autour
 *       ignorés, entrées vides écartées), aux cinq méthodes de
 *       l'API et aux en-têtes {@code Authorization} et {@code Content-Type}.</li>
 * </ul>
 */
@Configuration
public class SecurityConfig {

    @Value("${mdd.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Le filtre JWT est créé ici par {@code new} et n'est pas déclaré comme bean : Spring Boot
     * enregistre automatiquement tout bean de type {@code Filter} dans le conteneur de
     * servlets, ce qui le ferait aussi tourner en dehors de la chaîne de sécurité.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        }))
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
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
