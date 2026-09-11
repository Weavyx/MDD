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
        verify(userRepository).save(any(User.class));
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

    private RegisterRequest buildRequest(String email, String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail(email);
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }
}
