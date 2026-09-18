package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.AbstractContainerIT;
import com.openclassrooms.mddapi.model.Post;
import com.openclassrooms.mddapi.model.Topic;
import com.openclassrooms.mddapi.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class AbstractRepositoryIT extends AbstractContainerIT {

    @Autowired
    protected TestEntityManager entityManager;

    // Helpers de persistance communs aux *RepositoryIT (jeu de données minimal, flush immédiat).

    protected User persistUser(String email, String username) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setPasswordHash("hashed-password");
        entityManager.persistAndFlush(user);
        return user;
    }

    protected Topic persistTopic(String name, String description) {
        Topic topic = new Topic(name, description);
        entityManager.persistAndFlush(topic);
        return topic;
    }

    protected Post persistPost(String title, String content, User user, Topic topic) {
        Post post = new Post(title, content, user, topic);
        entityManager.persistAndFlush(post);
        return post;
    }
}
