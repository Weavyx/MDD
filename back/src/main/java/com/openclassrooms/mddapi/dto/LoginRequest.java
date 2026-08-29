package com.openclassrooms.mddapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "L'identifiant est obligatoire")
    @Size(max = 255, message = "L'identifiant ne doit pas dépasser 255 caractères")
    private String identifier;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(max = 255, message = "Le mot de passe ne doit pas dépasser 255 caractères")
    private String password;
}
