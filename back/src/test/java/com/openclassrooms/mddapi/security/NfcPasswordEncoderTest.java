package com.openclassrooms.mddapi.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class NfcPasswordEncoderTest {

    // « é » précomposé (U+00E9) et « e » suivi de l'accent aigu combinant (U+0301).
    private static final String NFC = "Mot-de-passe-été1!";
    private static final String NFD = "Mot-de-passe-été1!";

    // Coût 4 (le minimum) : seul le comportement de normalisation est testé ici.
    private final NfcPasswordEncoder encoder = new NfcPasswordEncoder(new BCryptPasswordEncoder(4));

    @Test
    void matches_motDePasseEncodeEnNfd_correspondAuMemeEnNfc() {
        assertThat(NFD).isNotEqualTo(NFC);

        String hash = encoder.encode(NFD);

        assertThat(encoder.matches(NFC, hash)).isTrue();
    }

    @Test
    void matches_motDePasseEncodeEnNfc_correspondAuMemeEnNfd() {
        // Sens nécessaire pour prouver la normalisation dans matches : dans l'autre sens,
        // c'est encode qui normalise et matches reçoit déjà la forme NFC.
        String hash = encoder.encode(NFC);

        assertThat(encoder.matches(NFD, hash)).isTrue();
    }

    @Test
    void matches_autreMotDePasse_false() {
        String hash = encoder.encode(NFC);

        assertThat(encoder.matches("Mot-de-passe-ete1!", hash)).isFalse();
    }
}
