package com.openclassrooms.mddapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserProfileResponse {
    private Long id;
    private String email;
    private String username;
    private List<TopicResponse> subscriptions;
}
