package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.AuthResponse;
import com.openclassrooms.mddapi.dto.RegisterRequest;
import com.openclassrooms.mddapi.exception.EmailAlreadyUsedException;
import com.openclassrooms.mddapi.exception.UsernameAlreadyUsedException;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import com.openclassrooms.mddapi.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_emailEtUsernameLibres_sauvegardeEtRetourneUnToken() {
        RegisterRequest request = buildRequest("alice@mail.com", "alice", "Password1!");
        when(userRepository.existsByEmail("alice@mail.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("Password1!")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(jwtService.generateToken("7")).thenReturn("jwt-token");

        AuthResponse result = authService.register(request);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("alice@mail.com");
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        // Garantie de sécurité : c'est le hash produit par l'encoder qui est stocké,
        // jamais le mot de passe en clair.
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hash");
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("Password1!");
    }

    @Test
    void register_emailDejaUtilise_emailAlreadyUsedExceptionEtSaveJamaisAppele() {
        RegisterRequest request = buildRequest("alice@mail.com", "alice", "Password1!");
        when(userRepository.existsByEmail("alice@mail.com")).thenReturn(true);

        assertThrows(EmailAlreadyUsedException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_usernameDejaUtilise_usernameAlreadyUsedExceptionEtSaveJamaisAppele() {
        RegisterRequest request = buildRequest("alice@mail.com", "alice", "Password1!");
        when(userRepository.existsByEmail("alice@mail.com")).thenReturn(false);
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(UsernameAlreadyUsedException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_identifiantsValides_retourneUnTokenGenereAvecLIdDuPrincipal() {
        com.openclassrooms.mddapi.dto.LoginRequest request =
                new com.openclassrooms.mddapi.dto.LoginRequest();
        request.setIdentifier("alice@mail.com");
        request.setPassword("Password1!");

        User user = new User();
        user.setId(42L);
        user.setEmail("alice@mail.com");
        user.setUsername("alice");
        user.setPasswordHash("hash");
        com.openclassrooms.mddapi.security.services.UserDetailsImpl principal =
                new com.openclassrooms.mddapi.security.services.UserDetailsImpl(user);

        org.springframework.security.core.Authentication authentication =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, java.util.Collections.emptyList());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateToken("42")).thenReturn("jwt-token");

        AuthResponse result = authService.login(request);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        // Le token d'authentification transmis doit porter l'identifiant en principal
        // et le mot de passe en credentials : une inversion serait indétectable sinon.
        ArgumentCaptor<org.springframework.security.core.Authentication> captor =
                ArgumentCaptor.forClass(org.springframework.security.core.Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("alice@mail.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo("Password1!");
    }

    @Test
    void login_identifiantsInvalides_propageBadCredentialsEtAucunTokenGenere() {
        com.openclassrooms.mddapi.dto.LoginRequest request =
                new com.openclassrooms.mddapi.dto.LoginRequest();
        request.setIdentifier("alice@mail.com");
        request.setPassword("MauvaisMdp1!");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authService.login(request));

        verify(jwtService, never()).generateToken(org.mockito.ArgumentMatchers.anyString());
    }

    private RegisterRequest buildRequest(String email, String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
