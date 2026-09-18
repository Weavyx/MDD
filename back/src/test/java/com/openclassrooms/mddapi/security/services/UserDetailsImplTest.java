package com.openclassrooms.mddapi.security.services;

import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDetailsImplTest {

    @Test
    void getters_delegueLesValeursAuUserEnveloppe() {
        User user = buildUser(42L, "alice@mail.com", "alice", "hash");

        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        assertThat(userDetails.getId()).isEqualTo(42L);
        assertThat(userDetails.getUsername()).isEqualTo("alice");
        assertThat(userDetails.getPassword()).isEqualTo("hash");
    }

    @Test
    void getUsername_retourneLeUsernamePasLEmail() {
        User user = buildUser(1L, "alice@mail.com", "alice", "hash");

        UserDetailsImpl userDetails = new UserDetailsImpl(user);

        assertThat(userDetails.getUsername()).isEqualTo("alice");
        assertThat(userDetails.getUsername()).isNotEqualTo("alice@mail.com");
    }

    @Test
    void getAuthorities_retourneUneCollectionVideAucunRoleDansLeMvp() {
        UserDetailsImpl userDetails = new UserDetailsImpl(buildUser(1L, "alice@mail.com", "alice", "hash"));

        assertThat(userDetails.getAuthorities()).isEmpty();
    }

    @Test
    void flagsDeCompte_tousActifsAucuneGestionDExpirationNiDeBlocage() {
        UserDetailsImpl userDetails = new UserDetailsImpl(buildUser(1L, "alice@mail.com", "alice", "hash"));

        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();
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
