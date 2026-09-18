package com.openclassrooms.mddapi.security.services;

import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void loadUserByUsername_identifiantEmail_retourneUnUserDetailsImplRenseigne() {
        User user = buildUser(42L, "alice@mail.com", "alice", "hash");
        when(userRepository.findByEmailOrUsername("alice@mail.com")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("alice@mail.com");

        assertThat(result).isInstanceOf(UserDetailsImpl.class);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getPassword()).isEqualTo("hash");
        assertThat(((UserDetailsImpl) result).getId()).isEqualTo(42L);
    }

    @Test
    void loadUserByUsername_identifiantUsername_retourneLeMemeUtilisateur() {
        User user = buildUser(42L, "alice@mail.com", "alice", "hash");
        when(userRepository.findByEmailOrUsername("alice")).thenReturn(Optional.of(user));

        UserDetails result = userDetailsService.loadUserByUsername("alice");

        assertThat(((UserDetailsImpl) result).getId()).isEqualTo(42L);
        assertThat(result.getUsername()).isEqualTo("alice");
    }

    @Test
    void loadUserByUsername_identifiantInconnu_usernameNotFoundException() {
        when(userRepository.findByEmailOrUsername("inconnu@mail.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("inconnu@mail.com"));
    }

    private User buildUser(Long id, String email, String username, String passwordHash) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        return user;
    }
}
