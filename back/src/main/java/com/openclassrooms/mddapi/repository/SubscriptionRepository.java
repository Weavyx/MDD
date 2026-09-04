package com.openclassrooms.mddapi.repository;

import com.openclassrooms.mddapi.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    boolean existsByUserIdAndTopicId(Long userId, Long topicId);
    List<Subscription> findByUserId(Long userId);

    @Query("SELECT s.topic.id FROM Subscription s WHERE s.user.id = :userId")
    List<Long> findSubscribedTopicIdsByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Subscription s WHERE s.user.id = :userId AND s.topic.id = :topicId")
    int deleteByUserIdAndTopicId(@Param("userId") Long userId, @Param("topicId") Long topicId);
}
