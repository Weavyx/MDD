package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.CreateCommentRequest;
import com.openclassrooms.mddapi.dto.CreatePostRequest;
import com.openclassrooms.mddapi.dto.PostDetailResponse;
import com.openclassrooms.mddapi.dto.PostSummaryResponse;
import com.openclassrooms.mddapi.exception.PostNotFoundException;
import com.openclassrooms.mddapi.exception.TopicNotFoundException;
import com.openclassrooms.mddapi.model.Comment;
import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.CommentRepository;
import com.openclassrooms.mddapi.repository.PostRepository;
import com.openclassrooms.mddapi.repository.TopicRepository;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void findFeed_ordreDesc_repositoryAppeleAvecTriDescSurCreatedAtEtMappingCorrect() {
        User author = new User();
        author.setUsername("alice");
        Topic topic = new Topic("Java", "Description Java");
        Post post = new Post("Titre", "Contenu", author, topic);
        setField(post, "id", 1L);
        setField(post, "createdAt", LocalDateTime.of(2026, 9, 1, 10, 0));
        when(postRepository.findPostsByUserId(10L, Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(post));

        List<PostSummaryResponse> result = postService.findFeed(10L, Sort.Direction.DESC);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTitle()).isEqualTo("Titre");
        assertThat(result.get(0).getExcerpt()).isEqualTo("Contenu");
        assertThat(result.get(0).getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 10, 0));
        assertThat(result.get(0).getTopicName()).isEqualTo("Java");
        assertThat(result.get(0).getAuthor()).isEqualTo("alice");
    }

    @Test
    void findFeed_contenuDeExactement200Caracteres_extraitNonTronque() {
        String content = "a".repeat(200);
        Post post = new Post("Titre", content, new User(), new Topic("Java", "Description Java"));
        when(postRepository.findPostsByUserId(10L, Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(post));

        List<PostSummaryResponse> result = postService.findFeed(10L, Sort.Direction.DESC);

        assertThat(result.get(0).getExcerpt()).isEqualTo(content);
    }

    @Test
    void findFeed_contenuDePlusDe200Caracteres_extraitTronqueA200SuiviDePointsDeSuspension() {
        String content = "a".repeat(200) + "b".repeat(50);
        Post post = new Post("Titre", content, new User(), new Topic("Java", "Description Java"));
        when(postRepository.findPostsByUserId(10L, Sort.by(Sort.Direction.DESC, "createdAt"))).thenReturn(List.of(post));

        List<PostSummaryResponse> result = postService.findFeed(10L, Sort.Direction.DESC);

        assertThat(result.get(0).getExcerpt()).isEqualTo("a".repeat(200) + "…");
        assertThat(result.get(0).getExcerpt()).hasSize(201);
    }

    @Test
    void findFeed_ordreAsc_repositoryAppeleAvecTriAscSurCreatedAt() {
        when(postRepository.findPostsByUserId(10L, Sort.by(Sort.Direction.ASC, "createdAt"))).thenReturn(List.of());

        List<PostSummaryResponse> result = postService.findFeed(10L, Sort.Direction.ASC);

        assertThat(result).isEmpty();
        verify(postRepository, times(1)).findPostsByUserId(10L, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    @Test
    void create_topicExistant_postSauvegardeAvecAuteurEtTopicEtIdRetourne() {
        Topic topic = new Topic("Java", "Description Java");
        setField(topic, "id", 1L);
        User user = new User();
        user.setId(10L);
        when(topicRepository.findById(1L)).thenReturn(Optional.of(topic));
        when(userRepository.getReferenceById(10L)).thenReturn(user);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            setField(saved, "id", 7L);
            return saved;
        });
        CreatePostRequest request = new CreatePostRequest();
        request.setTopicId(1L);
        request.setTitle("Mon article");
        request.setContent("Le contenu");

        Long postId = postService.create(10L, request);

        assertThat(postId).isEqualTo(7L);
        ArgumentCaptor<Post> captor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Mon article");
        assertThat(captor.getValue().getContent()).isEqualTo("Le contenu");
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getTopic()).isSameAs(topic);
    }

    @Test
    void create_topicInexistant_topicNotFoundExceptionEtSaveJamaisAppele() {
        when(topicRepository.findById(99L)).thenReturn(Optional.empty());
        CreatePostRequest request = new CreatePostRequest();
        request.setTopicId(99L);
        request.setTitle("Mon article");
        request.setContent("Le contenu");

        assertThrows(TopicNotFoundException.class, () -> postService.create(10L, request));

        verify(userRepository, never()).getReferenceById(anyLong());
        verify(postRepository, never()).save(any());
    }

    @Test
    void findById_articleExistant_detailAvecCommentairesDansLOrdreDuRepository() {
        User author = new User();
        author.setUsername("alice");
        Topic topic = new Topic("Java", "Description Java");
        Post post = new Post("Titre", "Contenu", author, topic);
        setField(post, "id", 5L);
        setField(post, "createdAt", LocalDateTime.of(2026, 9, 1, 10, 0));
        User commenter = new User();
        commenter.setUsername("bob");
        Comment first = new Comment(commenter, post, "Premier");
        setField(first, "id", 1L);
        setField(first, "createdAt", LocalDateTime.of(2026, 9, 1, 11, 0));
        Comment second = new Comment(commenter, post, "Second");
        setField(second, "id", 2L);
        setField(second, "createdAt", LocalDateTime.of(2026, 9, 1, 12, 0));
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdOrderByCreatedAtAsc(5L)).thenReturn(List.of(first, second));

        PostDetailResponse result = postService.findById(5L);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTitle()).isEqualTo("Titre");
        assertThat(result.getTopicName()).isEqualTo("Java");
        assertThat(result.getAuthor()).isEqualTo("alice");
        assertThat(result.getComments()).extracting("id", "content", "author")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, "Premier", "bob"),
                        org.assertj.core.groups.Tuple.tuple(2L, "Second", "bob")
                );
    }

    @Test
    void findById_articleInexistant_postNotFoundExceptionEtCommentairesJamaisCharges() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.findById(99L));

        verify(commentRepository, never()).findByPostIdOrderByCreatedAtAsc(anyLong());
    }

    @Test
    void addComment_articleExistant_commentaireSauvegardeAvecAuteurEtArticle() {
        User author = new User();
        Topic topic = new Topic("Java", "Description Java");
        Post post = new Post("Titre", "Contenu", author, topic);
        setField(post, "id", 5L);
        User user = new User();
        user.setId(10L);
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(userRepository.getReferenceById(10L)).thenReturn(user);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Super article");

        postService.addComment(10L, 5L, request);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("Super article");
        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getPost()).isSameAs(post);
    }

    @Test
    void addComment_articleInexistant_postNotFoundExceptionEtSaveJamaisAppele() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Super article");

        assertThrows(PostNotFoundException.class, () -> postService.addComment(10L, 99L, request));

        verify(userRepository, never()).getReferenceById(anyLong());
        verify(commentRepository, never()).save(any());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
