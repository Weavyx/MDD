package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.PostSummaryResponse;
import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.CommentRepository;
import com.openclassrooms.mddapi.repository.PostRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Seule règle de {@link PostService} gardée en test unitaire : la borne exacte de l'extrait du
 * fil (200 caractères, puis « … »). Le reste du service est vérifié par les tests d'intégration
 * du package {@code parcours}.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private TopicRepository topicRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PostService postService;

    @Test
    void findFeed_contenuDeExactement200Caracteres_extraitNonTronque() {
        // Arrange
        String content = "a".repeat(200);
        Post post = new Post("Titre", content, new User(), new Topic("Java", "Description Java"));
        when(postRepository.findPostsByUserId(10L, Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(post));

        // Act
        List<PostSummaryResponse> result = postService.findFeed(10L, Sort.Direction.DESC);

        // Assert
        assertThat(result.get(0).getExcerpt()).isEqualTo(content);
    }

    @Test
    void findFeed_contenuDePlusDe200Caracteres_extraitTronqueA200SuiviDePointsDeSuspension() {
        // Arrange
        String content = "a".repeat(200) + "b".repeat(50);
        Post post = new Post("Titre", content, new User(), new Topic("Java", "Description Java"));
        when(postRepository.findPostsByUserId(10L, Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(post));

        // Act
        List<PostSummaryResponse> result = postService.findFeed(10L, Sort.Direction.DESC);

        // Assert
        assertThat(result.get(0).getExcerpt()).isEqualTo("a".repeat(200) + "…");
        assertThat(result.get(0).getExcerpt()).hasSize(201);
    }
}
