package com.openclassrooms.mddapi.dto;

import com.openclassrooms.mddapi.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères")
    private String username;

    @NotBlank(message = "L'adresse e-mail est obligatoire")
    @Size(max = 255, message = "L'adresse e-mail ne doit pas dépasser 255 caractères")
    @Email(message = "L'adresse e-mail doit être valide")
    private String email;

    /**
     * Le mot de passe doit contenir au moins 8 caractères, une majuscule,
     * une minuscule, un chiffre et un caractère spécial (règle imposée par
     * les spécifications fonctionnelles du projet MDD).
     * <p>
     * Les caractères spéciaux acceptés correspondent à la classe POSIX Java
     * {@code \p{Punct}}, qui reproduit exactement la liste de référence
     * publiée par OWASP : {@code ! " # $ % & ' ( ) * + , - . / : ; < = > ? @ [ \ ] ^ _ ` { | } ~}
     * (voir <a href="https://owasp.org/www-community/password-special-characters">
     * OWASP - Password Special Characters</a>).
     * <p>
     * Borne haute fixée à 72 octets UTF-8 (forme NFC), et non 72 caractères : BCrypt
     * refuse au-delà de 72 octets (exception à l'encodage, donc 500), et un caractère
     * accentué en occupe 2.
     */
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    @MaxUtf8Bytes(72)
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*\\p{Punct}).{8,}$",
            message = "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial"
    )
    private String password;
}
