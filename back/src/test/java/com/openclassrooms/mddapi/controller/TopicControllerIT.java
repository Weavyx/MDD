package com.openclassrooms.mddapi.controller;

import com.openclassrooms.mddapi.dto.TopicResponse;
import com.openclassrooms.mddapi.exception.AlreadySubscribedException;
import com.openclassrooms.mddapi.exception.GlobalExceptionHandler;
import com.openclassrooms.mddapi.exception.TopicNotFoundException;
import com.openclassrooms.mddapi.security.SecurityConfig;
import com.openclassrooms.mddapi.service.TopicService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TopicController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TopicControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void findAll_avecJwtValide_retourne200EtLaListeDeTopics() throws Exception {
        List<TopicResponse> topics = List.of(
                new TopicResponse(1L, "Java", "Description Java", true),
                new TopicResponse(2L, "Angular", "Description Angular", false)
        );
        when(topicService.findAllWithSubscriptionStatus(1L)).thenReturn(topics);

        mockMvc.perform(get("/api/topics").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Java"))
                .andExpect(jsonPath("$[0].subscribed").value(true))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].subscribed").value(false));
    }

    @Test
    void findAll_sansJwt_retourne401() throws Exception {
        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_avecJwtValideEtSucces_retourne200() throws Exception {
        doNothing().when(topicService).subscribe(1L, 1L);

        mockMvc.perform(post("/api/topics/{id}/subscription", 1L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isOk());
    }

    @Test
    void subscribe_topicNotFoundException_retourne404() throws Exception {
        doThrow(new TopicNotFoundException("Ce topic n'existe pas"))
                .when(topicService).subscribe(1L, 99L);

        mockMvc.perform(post("/api/topics/{id}/subscription", 99L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscribe_alreadySubscribedException_retourne409() throws Exception {
        doThrow(new AlreadySubscribedException("Vous êtes déjà abonné à ce topic"))
                .when(topicService).subscribe(1L, 1L);

        mockMvc.perform(post("/api/topics/{id}/subscription", 1L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isConflict());
    }

    @Test
    void subscribe_sansJwt_retourne401() throws Exception {
        mockMvc.perform(post("/api/topics/{id}/subscription", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_idNonNumeriqueDansUrl_retourne400() throws Exception {
        mockMvc.perform(post("/api/topics/{id}/subscription", "abc").with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsubscribe_avecJwtValideEtSucces_retourne204() throws Exception {
        doNothing().when(topicService).unsubscribe(1L, 1L);

        mockMvc.perform(delete("/api/topics/{id}/subscription", 1L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void unsubscribe_topicNotFoundException_retourne404() throws Exception {
        doThrow(new TopicNotFoundException("Ce topic n'existe pas"))
                .when(topicService).unsubscribe(1L, 99L);

        mockMvc.perform(delete("/api/topics/{id}/subscription", 99L).with(jwt().jwt(jwt -> jwt.subject("1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unsubscribe_sansJwt_retourne401() throws Exception {
        mockMvc.perform(delete("/api/topics/{id}/subscription", 1L))
                .andExpect(status().isUnauthorized());
    }
}
