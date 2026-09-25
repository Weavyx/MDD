package com.openclassrooms.mddapi.security.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.JWKSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Encodeur et décodeur JWT partageant une même clé symétrique HMAC-SHA256.
 * <p>
 * La clé est {@code mdd.jwt.secret}, attendue en Base64 ; elle n'est pas définie dans
 * {@code application.properties} mais dans le profil {@code local} (fichier gitignoré) via
 * la variable d'environnement {@code JWT_SECRET}. Sans cette variable, le contexte ne
 * démarre pas — y compris pour {@code MddApiApplicationIT}. La longueur de la clé décodée
 * n'est pas contrôlée ici : HS256 exige au moins 256 bits, à garantir à la génération.
 * <p>
 * Le décodeur ne vérifie que la signature et l'expiration ({@code exp}) : le claim
 * {@code iss} émis par {@link JwtService} n'est pas validé (aucun {@code JwtIssuerValidator}),
 * et aucune révocation n'existe — un jeton reste valable jusqu'à son {@code exp}.
 */
@Configuration
public class JwtConfig {

    @Value("${mdd.jwt.secret}")
    private String jwtSecret;

    @Bean
    public JwtEncoder jwtEncoder() {
        SecretKeySpec secretKey = buildSecretKey();
        JWKSource<SecurityContext> jwkSource = new ImmutableSecret<>(secretKey);
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKeySpec secretKey = buildSecretKey();
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }

    private  SecretKeySpec buildSecretKey(){
        return new SecretKeySpec(
                Base64.getDecoder().decode(jwtSecret), "HmacSHA256");
    }
}