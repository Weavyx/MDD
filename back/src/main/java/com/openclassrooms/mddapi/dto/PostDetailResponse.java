package com.openclassrooms.mddapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class PostDetailResponse {
    private Long id;
    private String title;
    private String content;
    private LocalDateTime createdAt;
    private String topicName;
    private String author;
    private List<CommentResponse> comments;
}
