package com.openclassrooms.mddapi.dto;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class AuthenticatedUserResponse {
    private final Long id;
    private final String username;
}
