package com.openclassrooms.mddapi.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Borne la longueur d'une chaîne en octets UTF-8, après normalisation NFC, et non en
 * caractères comme {@code @Size}. Sert au mot de passe : BCrypt refuse au-delà de
 * 72 octets (exception à l'encodage), et un caractère accentué en occupe 2.
 * {@code null} est valide, comme pour les contraintes standard.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "Le mot de passe ne doit pas dépasser {value} octets (un caractère accentué en compte 2)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
