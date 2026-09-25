package com.openclassrooms.mddapi.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaxUtf8BytesValidatorTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private record Holder(@MaxUtf8Bytes(72) String password) {
    }

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private boolean isValid(String password) {
        return validator.validate(new Holder(password)).isEmpty();
    }

    @Test
    void isValid_72CaracteresAscii_exactement72Octets_valide() {
        assertThat(isValid("a".repeat(72))).isTrue();
    }

    @Test
    void isValid_73CaracteresAscii_invalide() {
        assertThat(isValid("a".repeat(73))).isFalse();
    }

    @Test
    void isValid_36EAccentPrecompose_exactement72Octets_valide() {
        assertThat(isValid("é".repeat(36))).isTrue();
    }

    @Test
    void isValid_37EAccentPrecompose_74Octets_invalide() {
        assertThat(isValid("é".repeat(37))).isFalse();
    }

    @Test
    void isValid_36EAccentEnNfd_compteEnFormeNfc_valide() {
        // En NFD, "é" pèse 3 octets (108 au total) ; après NFC, 2 octets (72).
        assertThat(isValid("é".repeat(36))).isTrue();
    }

    @Test
    void isValid_null_valide() {
        assertThat(isValid(null)).isTrue();
    }

    @Test
    void message_indiqueLaBorneEnOctets() {
        assertThat(validator.validate(new Holder("a".repeat(73))))
                .singleElement()
                .extracting(violation -> violation.getMessage())
                .isEqualTo("Le mot de passe ne doit pas dépasser 72 octets (un caractère accentué en compte 2)");
    }
}
