package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Topic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class TopicRepositoryIT extends AbstractRepositoryIT {

    @Autowired
    private TopicRepository topicRepository;

    @Test
    void findAll_apresMigrationsFlyway_contientLesHuitThemesDeReference() {
        var names = topicRepository.findAll().stream().map(Topic::getName).toList();

        assertThat(names).containsExactlyInAnyOrder(
                "Java", "JavaScript", "Python", "Web3", "Angular", "Spring", "DevOps", "Sécurité");
    }
}
