package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.PostSummaryResponse;
import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.dto.UpdateProfileRequest;
import com.openclassrooms.mddapi.dto.UserProfileResponse;
import com.openclassrooms.mddapi.dto.UserResponse;
import com.openclassrooms.mddapi.exception.AlreadySubscribedException;
import com.openclassrooms.mddapi.exception.EmailAlreadyUsedException;
import com.openclassrooms.mddapi.exception.GlobalExceptionHandler;
import com.openclassrooms.mddapi.exception.TopicNotFoundException;
import com.openclassrooms.mddapi.exception.UserNotFoundException;
import com.openclassrooms.mddapi.exception.UsernameAlreadyUsedException;
import com.openclassrooms.mddapi.security.SecurityConfig;
import com.openclassrooms.mddapi.service.PostService;
import com.openclassrooms.mddapi.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private PostService postService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void getProfile_avecJwtValideEtAbonnements_retourne200EtLeProfil() throws Exception {
        UserProfileResponse profile = new UserProfileResponse(1L, "alice@mail.com", "alice", List.of(
                new TopicResponse(10L, "Java", "Description Java", true),
                new TopicResponse(20L, "Angular", "Description Angular", true)
        ));
        when(userService.getProfile(1L)).thenReturn(profile);

        mockMvc.perform(get("/api/users/me").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice@mail.com"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.subscriptions", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.subscriptions[0].id").value(10))
                .andExpect(jsonPath("$.subscriptions[0].name").value("Java"))
                .andExpect(jsonPath("$.subscriptions[0].subscribed").value(true))
                .andExpect(jsonPath("$.subscriptions[1].id").value(20));
    }

    @Test
    void getProfile_avecJwtValideSansAbonnement_retourne200EtListeVide() throws Exception {
        when(userService.getProfile(1L)).thenReturn(new UserProfileResponse(1L, "alice@mail.com", "alice", List.of()));

        mockMvc.perform(get("/api/users/me").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.subscriptions", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void getProfile_userNotFoundException_retourne404() throws Exception {
        when(userService.getProfile(99L)).thenThrow(new UserNotFoundException("Cet utilisateur n'existe pas"));

        mockMvc.perform(get("/api/users/me").with(jwt().jwt(jwt -> jwt.subject("99"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cet utilisateur n'existe pas"));
    }

    @Test
    void getProfile_sansJwt_retourne401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_emailEtUsernameSansMotDePasse_retourne200EtAppelleLeServiceAvecPasswordNull() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("alice2@mail.com");
        request.setUsername("alice2");
        when(userService.updateProfile(1L, request)).thenReturn(new UserResponse(1L, "alice2@mail.com", "alice2"));

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice2@mail.com\",\"username\":\"alice2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("alice2@mail.com"))
                .andExpect(jsonPath("$.username").value("alice2"));

        verify(userService).updateProfile(1L, request);
    }

    @Test
    void updateProfile_avecMotDePasseValide_retourne200EtAppelleLeServiceAvecLeMotDePasse() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setEmail("alice@mail.com");
        request.setUsername("alice");
        request.setPassword("NewPass1!");
        when(userService.updateProfile(1L, request)).thenReturn(new UserResponse(1L, "alice@mail.com", "alice"));

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"NewPass1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@mail.com"));

        verify(userService).updateProfile(1L, request);
    }

    @Test
    void updateProfile_emailAlreadyUsedException_retourne409() throws Exception {
        when(userService.updateProfile(anyLong(), any(UpdateProfileRequest.class)))
                .thenThrow(new EmailAlreadyUsedException("Cet email est déjà utilisé"));

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bob@mail.com\",\"username\":\"alice\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Cet email est déjà utilisé"));
    }

    @Test
    void updateProfile_usernameAlreadyUsedException_retourne409() throws Exception {
        when(userService.updateProfile(anyLong(), any(UpdateProfileRequest.class)))
                .thenThrow(new UsernameAlreadyUsedException("Ce nom d'utilisateur est déjà utilisé"));

        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"bob\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Ce nom d'utilisateur est déjà utilisé"));
    }

    @Test
    void updateProfile_motDePasseNonConformeAuPattern_retourne400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"password\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void updateProfile_motDePasseTropCourt_retourne400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\",\"password\":\"Ab1!\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void updateProfile_emailInvalide_retourne400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pas-un-email\",\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void updateProfile_usernameTropCourt_retourne400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"al\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void updateProfile_emailAbsent_retourne400() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .with(jwt().jwt(jwt -> jwt.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateProfile(anyLong(), any());
    }

    @Test
    void updateProfile_sansJwt_retourne401() throws Exception {
        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alice@mail.com\",\"username\":\"alice\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_avecJwtValideEtSucces_retourne200() throws Exception {
        doNothing().when(userService).subscribe(1L, 1L);

        mockMvc.perform(post("/api/users/me/subscriptions/{topicId}", 1L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk());
    }

    @Test
    void subscribe_topicNotFoundException_retourne404() throws Exception {
        doThrow(new TopicNotFoundException("Ce topic n'existe pas"))
                .when(userService).subscribe(1L, 99L);

        mockMvc.perform(post("/api/users/me/subscriptions/{topicId}", 99L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscribe_alreadySubscribedException_retourne409() throws Exception {
        doThrow(new AlreadySubscribedException("Vous êtes déjà abonné à ce topic"))
                .when(userService).subscribe(1L, 1L);

        mockMvc.perform(post("/api/users/me/subscriptions/{topicId}", 1L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isConflict());
    }

    @Test
    void subscribe_sansJwt_retourne401() throws Exception {
        mockMvc.perform(post("/api/users/me/subscriptions/{topicId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_topicIdNonNumeriqueDansUrl_retourne400() throws Exception {
        mockMvc.perform(post("/api/users/me/subscriptions/{topicId}", "abc").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsubscribe_avecJwtValideEtSucces_retourne204() throws Exception {
        doNothing().when(userService).unsubscribe(1L, 1L);

        mockMvc.perform(delete("/api/users/me/subscriptions/{topicId}", 1L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void unsubscribe_topicNotFoundException_retourne404() throws Exception {
        doThrow(new TopicNotFoundException("Ce topic n'existe pas"))
                .when(userService).unsubscribe(1L, 99L);

        mockMvc.perform(delete("/api/users/me/subscriptions/{topicId}", 99L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsubscribe_sansJwt_retourne401() throws Exception {
        mockMvc.perform(delete("/api/users/me/subscriptions/{topicId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void findFeed_sansParametreSort_retourne200EtLeFilTrieDesc() throws Exception {
        List<PostSummaryResponse> feed = List.of(
                new PostSummaryResponse(2L, "Titre 2", "Extrait 2", LocalDateTime.of(2026, 9, 2, 10, 0), "Java", "alice"),
                new PostSummaryResponse(1L, "Titre 1", "Extrait 1", LocalDateTime.of(2026, 9, 1, 10, 0), "Angular", "bob")
        );
        when(postService.findFeed(1L, Sort.Direction.DESC)).thenReturn(feed);

        mockMvc.perform(get("/api/users/me/feed").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].title").value("Titre 2"))
                .andExpect(jsonPath("$[0].excerpt").value("Extrait 2"))
                .andExpect(jsonPath("$[0].topicName").value("Java"))
                .andExpect(jsonPath("$[0].author").value("alice"))
                .andExpect(jsonPath("$[1].id").value(1));
    }

    @Test
    void findFeed_sortAsc_retourne200EtAppelleLeServiceAvecAsc() throws Exception {
        List<PostSummaryResponse> feed = List.of(
                new PostSummaryResponse(1L, "Titre 1", "Extrait 1", LocalDateTime.of(2026, 9, 1, 10, 0), "Angular", "bob")
        );
        when(postService.findFeed(1L, Sort.Direction.ASC)).thenReturn(feed);

        mockMvc.perform(get("/api/users/me/feed").param("sort", "asc").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(1));

        verify(postService).findFeed(1L, Sort.Direction.ASC);
    }

    @Test
    void findFeed_sortDesc_retourne200EtAppelleLeServiceAvecDesc() throws Exception {
        when(postService.findFeed(1L, Sort.Direction.DESC)).thenReturn(List.of());

        mockMvc.perform(get("/api/users/me/feed").param("sort", "desc").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));

        verify(postService).findFeed(1L, Sort.Direction.DESC);
    }

    @Test
    void findFeed_sortInvalide_retourne400() throws Exception {
        mockMvc.perform(get("/api/users/me/feed").param("sort", "random").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verify(postService, never()).findFeed(anyLong(), any());
    }

    @Test
    void findFeed_sansJwt_retourne401() throws Exception {
        mockMvc.perform(get("/api/users/me/feed"))
                .andExpect(status().isUnauthorized());
    }
}
