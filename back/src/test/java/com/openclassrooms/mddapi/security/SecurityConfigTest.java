package com.openclassrooms.mddapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    private CorsConfiguration corsConfiguration(String allowedOrigins) {
        SecurityConfig securityConfig = new SecurityConfig();
        ReflectionTestUtils.setField(securityConfig, "allowedOrigins", allowedOrigins);
        return securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/topics"));
    }

    @Test
    void corsConfigurationSource_originesSepareesParVirguleEtEspace_toutesAutorisees() {
        // Valeur typique de MDD_CORS_ALLOWED_ORIGINS écrite à la main.
        CorsConfiguration configuration = corsConfiguration("http://a.test, http://b.test");

        assertThat(configuration.checkOrigin("http://a.test")).isEqualTo("http://a.test");
        assertThat(configuration.checkOrigin("http://b.test")).isEqualTo("http://b.test");
    }

    @Test
    void corsConfigurationSource_entreesVides_ignorees() {
        CorsConfiguration configuration = corsConfiguration("http://a.test,, ,");

        assertThat(configuration.getAllowedOrigins()).containsExactly("http://a.test");
    }
}
