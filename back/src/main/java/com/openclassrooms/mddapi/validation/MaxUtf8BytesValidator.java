package com.openclassrooms.mddapi.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Compte les octets de la forme NFC, celle que {@code NfcPasswordEncoder} transmet à BCrypt.
 */
public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, String> {

    private int max;

    @Override
    public void initialize(MaxUtf8Bytes constraintAnnotation) {
        this.max = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC).getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
