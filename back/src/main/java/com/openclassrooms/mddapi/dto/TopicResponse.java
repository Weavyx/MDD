package com.openclassrooms.mddapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopicResponse {
    private Long id;
    private String name;
    private String description;
    private boolean subscribed;
}
