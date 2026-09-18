package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.AbstractContainerIT;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
abstract class AbstractRepositoryIT extends AbstractContainerIT {
}
