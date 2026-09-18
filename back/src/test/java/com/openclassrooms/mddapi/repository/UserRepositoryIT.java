package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmailOrUsername_rechercheParEmail_trouveLUtilisateur() {
        User user = persistUser("alice@mail.com", "alice");

        Optional<User> found = userRepository.findByEmailOrUsername("alice@mail.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    @Test
    void findByEmailOrUsername_rechercheParUsername_trouveLUtilisateur() {
        User user = persistUser("alice@mail.com", "alice");

        Optional<User> found = userRepository.findByEmailOrUsername("alice");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(user.getId());
    }

    @Test
    void findByEmailOrUsername_aucuneCorrespondance_retourneOptionalVide() {
        persistUser("alice@mail.com", "alice");

        Optional<User> found = userRepository.findByEmailOrUsername("inconnu");

        assertThat(found).isEmpty();
    }

    @Test
    void existsByEmail_emailPresent_retourneTrue() {
        persistUser("alice@mail.com", "alice");

        assertThat(userRepository.existsByEmail("alice@mail.com")).isTrue();
    }

    @Test
    void existsByEmail_emailAbsent_retourneFalse() {
        assertThat(userRepository.existsByEmail("inconnu@mail.com")).isFalse();
    }

    @Test
    void existsByUsername_usernamePresent_retourneTrue() {
        persistUser("alice@mail.com", "alice");

        assertThat(userRepository.existsByUsername("alice")).isTrue();
    }

    @Test
    void existsByUsername_usernameAbsent_retourneFalse() {
        assertThat(userRepository.existsByUsername("inconnu")).isFalse();
    }

    @Test
    void existsByEmailAndIdNot_memeUtilisateurEmailInchange_retourneFalse() {
        User user = persistUser("alice@mail.com", "alice");

        boolean exists = userRepository.existsByEmailAndIdNot("alice@mail.com", user.getId());

        assertThat(exists).isFalse();
    }

    @Test
    void existsByEmailAndIdNot_autreUtilisateurPossedeDejaCetEmail_retourneTrue() {
        User alice = persistUser("alice@mail.com", "alice");
        User bob = persistUser("bob@mail.com", "bob");

        boolean exists = userRepository.existsByEmailAndIdNot(alice.getEmail(), bob.getId());

        assertThat(exists).isTrue();
    }

    @Test
    void existsByUsernameAndIdNot_memeUtilisateurUsernameInchange_retourneFalse() {
        User user = persistUser("alice@mail.com", "alice");

        boolean exists = userRepository.existsByUsernameAndIdNot("alice", user.getId());

        assertThat(exists).isFalse();
    }

    @Test
    void existsByUsernameAndIdNot_autreUtilisateurPossedeDejaCeUsername_retourneTrue() {
        User alice = persistUser("alice@mail.com", "alice");
        User bob = persistUser("bob@mail.com", "bob");

        boolean exists = userRepository.existsByUsernameAndIdNot(alice.getUsername(), bob.getId());

        assertThat(exists).isTrue();
    }
}
