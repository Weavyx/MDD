package com.openclassrooms.mddapi.dto;

import com.openclassrooms.mddapi.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Le nom d'utilisateur est obligatoire")
    @Size(min = 3, max = 50, message = "Le nom d'utilisateur doit contenir entre 3 et 50 caractères")
    private String username;

    @NotBlank(message = "L'adresse e-mail est obligatoire")
    @Size(max = 255, message = "L'adresse e-mail ne doit pas dépasser 255 caractères")
    @Email(message = "L'adresse e-mail doit être valide")
    private String email;

    /**
     * Optionnel : absent ou {@code null} signifie "ne pas changer le mot de passe".
     * Vide ou blanc, il est rejeté (400) par {@code @Size(min = 8)} et {@code @Pattern}.
     * Sinon, il doit respecter les mêmes règles que lors de l'inscription
     * (voir {@link RegisterRequest#getPassword()}).
     * <p>
     * L'absence de {@code @NotBlank} est volontaire, contrairement à
     * {@code RegisterRequest.password} : {@code UserService.updateProfile} conserve
     * le hash existant quand ce champ est {@code null}. Ajouter {@code @NotBlank} ici
     * obligerait à ressaisir le mot de passe à chaque modification du profil.
     */
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caractères")
    @MaxUtf8Bytes(72)
    @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*\\p{Punct}).{8,}$",
            message = "Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule, un chiffre et un caractère spécial"
    )
    private String password;
}
