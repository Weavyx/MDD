package com.openclassrooms.mddapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class PostSummaryResponse {
    private Long id;
    private String title;
    private String excerpt;
    private LocalDateTime createdAt;
    private String topicName;
    private String author;
}
