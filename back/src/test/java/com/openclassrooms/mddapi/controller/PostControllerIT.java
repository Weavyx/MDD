package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.CommentResponse;
import com.openclassrooms.mddapi.dto.CreateCommentRequest;
import com.openclassrooms.mddapi.dto.CreatePostRequest;
import com.openclassrooms.mddapi.dto.PostDetailResponse;
import com.openclassrooms.mddapi.exception.GlobalExceptionHandler;
import com.openclassrooms.mddapi.exception.PostNotFoundException;
import com.openclassrooms.mddapi.exception.TopicNotFoundException;
import com.openclassrooms.mddapi.security.SecurityConfig;
import com.openclassrooms.mddapi.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PostControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void create_avecJwtValideEtCorpsValide_retourne201EtLocation() throws Exception {
        CreatePostRequest request = new CreatePostRequest();
        request.setTopicId(3L);
        request.setTitle("Mon article");
        request.setContent("Le contenu de mon article");
        when(postService.create(1L, request)).thenReturn(7L);

        mockMvc.perform(post("/api/posts")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"title\":\"Mon article\",\"content\":\"Le contenu de mon article\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/posts/7")));
    }

    @Test
    void create_topicNotFoundException_retourne404() throws Exception {
        when(postService.create(anyLong(), any(CreatePostRequest.class)))
                .thenThrow(new TopicNotFoundException("Ce topic n'existe pas"));

        mockMvc.perform(post("/api/posts")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":99,\"title\":\"Mon article\",\"content\":\"Le contenu\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce topic n'existe pas"));
    }

    @Test
    void create_titreVide_retourne400() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"title\":\"   \",\"content\":\"Le contenu\"}"))
                .andExpect(status().isBadRequest())
                // Contrat d'erreur de validation : fieldErrors indexé par nom de champ du DTO,
                // valeur = message déclaré dans l'annotation (affichage par champ côté front).
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Requête invalide"))
                .andExpect(jsonPath("$.fieldErrors").isMap())
                .andExpect(jsonPath("$.fieldErrors.title").value("Le titre est obligatoire"));

        verify(postService, never()).create(anyLong(), any());
    }

    @Test
    void create_contenuVide_retourne400EtServiceJamaisAppele() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"title\":\"Mon article\",\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.content").value("Le contenu est obligatoire"));

        verify(postService, never()).create(anyLong(), any());
    }

    @Test
    void create_topicIdAbsent_retourne400() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Mon article\",\"content\":\"Le contenu\"}"))
                .andExpect(status().isBadRequest());

        verify(postService, never()).create(anyLong(), any());
    }

    @Test
    void create_sansJwt_retourne401() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":3,\"title\":\"Mon article\",\"content\":\"Le contenu\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findById_articleExistant_retourne200EtLeDetailAvecCommentaires() throws Exception {
        PostDetailResponse detail = new PostDetailResponse(
                5L, "Titre", "Contenu", LocalDateTime.of(2026, 9, 1, 10, 0), "Java", "alice",
                List.of(
                        new CommentResponse(1L, "Premier commentaire", LocalDateTime.of(2026, 9, 1, 11, 0), "bob"),
                        new CommentResponse(2L, "Second commentaire", LocalDateTime.of(2026, 9, 1, 12, 0), "carol")
                )
        );
        when(postService.findById(5L)).thenReturn(detail);

        mockMvc.perform(get("/api/posts/{id}", 5L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Titre"))
                .andExpect(jsonPath("$.topicName").value("Java"))
                .andExpect(jsonPath("$.author").value("alice"))
                .andExpect(jsonPath("$.comments", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.comments[0].content").value("Premier commentaire"))
                .andExpect(jsonPath("$.comments[0].author").value("bob"))
                .andExpect(jsonPath("$.comments[1].author").value("carol"));
    }

    @Test
    void findById_postNotFoundException_retourne404() throws Exception {
        when(postService.findById(99L)).thenThrow(new PostNotFoundException("Cet article n'existe pas"));

        mockMvc.perform(get("/api/posts/{id}", 99L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet article n'existe pas"));
    }

    @Test
    void findById_idNonNumeriqueDansUrl_retourne400() throws Exception {
        mockMvc.perform(get("/api/posts/{id}", "abc").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Le paramètre fourni est invalide"));

        verify(postService, never()).findById(anyLong());
    }

    @Test
    void findById_sansJwt_retourne401() throws Exception {
        mockMvc.perform(get("/api/posts/{id}", 5L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addComment_avecJwtValideEtCorpsValide_retourne201() throws Exception {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("Super article");
        doNothing().when(postService).addComment(1L, 5L, request);

        mockMvc.perform(post("/api/posts/{id}/comments", 5L)
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Super article\"}"))
                .andExpect(status().isCreated());

        verify(postService).addComment(1L, 5L, request);
    }

    @Test
    void addComment_postNotFoundException_retourne404() throws Exception {
        doThrow(new PostNotFoundException("Cet article n'existe pas"))
                .when(postService).addComment(anyLong(), anyLong(), any(CreateCommentRequest.class));

        mockMvc.perform(post("/api/posts/{id}/comments", 99L)
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Super article\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet article n'existe pas"));
    }

    @Test
    void addComment_contenuVide_retourne400() throws Exception {
        mockMvc.perform(post("/api/posts/{id}/comments", 5L)
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(postService, never()).addComment(anyLong(), anyLong(), any());
    }

    @Test
    void addComment_sansJwt_retourne401() throws Exception {
        mockMvc.perform(post("/api/posts/{id}/comments", 5L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Super article\"}"))
                .andExpect(status().isUnauthorized());
    }
}
