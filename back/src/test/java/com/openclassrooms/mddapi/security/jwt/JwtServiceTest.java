package com.openclassrooms.mddapi.security.jwt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    private static final String ISSUER = "mdd-api";
    private static final long EXPIRATION_MINUTES = 60L;

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private JwtService jwtService;

    private void injectConfig() {
        ReflectionTestUtils.setField(jwtService, "jwtIssuer", ISSUER);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMinutes", EXPIRATION_MINUTES);
    }

    @Test
    void generateToken_appelEncodeEtRetourneLeTokenValue() {
        injectConfig();
        Jwt fakeJwt = Jwt.withTokenValue("fake-token")
                .header("alg", "HS256")
                .claim("sub", "42")
                .build();
        when(jwtEncoder.encode(any())).thenReturn(fakeJwt);

        String result = jwtService.generateToken("42");

        assertThat(result).isEqualTo("fake-token");
    }

    @Test
    void generateToken_construitLesClaimsAvecIssuerEtSubjectCorrects() {
        injectConfig();
        Jwt fakeJwt = Jwt.withTokenValue("fake-token").header("alg", "HS256").claim("sub", "42").build();
        when(jwtEncoder.encode(any())).thenReturn(fakeJwt);
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);

        jwtService.generateToken("42");

        org.mockito.Mockito.verify(jwtEncoder).encode(captor.capture());
        JwtClaimsSet claims = captor.getValue().getClaims();
        assertThat((String) claims.getClaim("iss")).isEqualTo(ISSUER);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.getIssuedAt()).isNotNull();
    }

    @Test
    void generateToken_expirationCorrespondALaDureeConfiguree() {
        injectConfig();
        Jwt fakeJwt = Jwt.withTokenValue("fake-token").header("alg", "HS256").claim("sub", "42").build();
        when(jwtEncoder.encode(any())).thenReturn(fakeJwt);
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);
        Instant before = Instant.now();

        jwtService.generateToken("42");

        org.mockito.Mockito.verify(jwtEncoder).encode(captor.capture());
        JwtClaimsSet claims = captor.getValue().getClaims();
        Instant expectedExpiry = before.plus(EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        assertThat(claims.getExpiresAt())
                .isCloseTo(expectedExpiry, org.assertj.core.api.Assertions.within(2, ChronoUnit.SECONDS));
    }

    @Test
    void generateToken_utiliseAlgorithmeHS256() {
        injectConfig();
        Jwt fakeJwt = Jwt.withTokenValue("fake-token").header("alg", "HS256").claim("sub", "42").build();
        when(jwtEncoder.encode(any())).thenReturn(fakeJwt);
        ArgumentCaptor<JwtEncoderParameters> captor = ArgumentCaptor.forClass(JwtEncoderParameters.class);

        jwtService.generateToken("42");

        org.mockito.Mockito.verify(jwtEncoder).encode(captor.capture());
        assertThat(captor.getValue().getJwsHeader().getAlgorithm()).isEqualTo(MacAlgorithm.HS256);
    }
}
