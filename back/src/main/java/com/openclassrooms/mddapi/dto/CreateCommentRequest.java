package com.openclassrooms.mddapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentRequest {
    @NotBlank(message = "Le contenu est obligatoire")
    @Size(max = 1000, message = "Le contenu ne doit pas dépasser 1000 caractères")
    private String content;
}
