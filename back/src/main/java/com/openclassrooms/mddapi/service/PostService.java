package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.CommentResponse;
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
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PostService {
    private static final int EXCERPT_MAX_LENGTH = 200;

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    public PostService(PostRepository postRepository, CommentRepository commentRepository, TopicRepository topicRepository, UserRepository userRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.topicRepository = topicRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PostSummaryResponse> findFeed(Long userId, Sort.Direction direction) {
        return postRepository.findPostsByUserId(userId, Sort.by(direction, "createdAt")).stream()
                .map(post -> new PostSummaryResponse(
                        post.getId(),
                        post.getTitle(),
                        excerpt(post.getContent()),
                        post.getCreatedAt(),
                        post.getTopic().getName(),
                        post.getUser().getUsername()
                ))
                .toList();
    }

    private String excerpt(String content) {
        if (content.length() <= EXCERPT_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, EXCERPT_MAX_LENGTH) + "…";
    }

    @Transactional
    public Long create(Long userId, CreatePostRequest request) {
        Topic topic = topicRepository.findById(request.getTopicId())
                .orElseThrow(() -> new TopicNotFoundException("Ce topic n'existe pas"));

        User user = userRepository.getReferenceById(userId);
        Post post = new Post(request.getTitle(), request.getContent(), user, topic);
        return postRepository.save(post).getId();
    }

    @Transactional(readOnly = true)
    public PostDetailResponse findById(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Cet article n'existe pas"));

        List<CommentResponse> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(comment -> new CommentResponse(
                        comment.getId(),
                        comment.getContent(),
                        comment.getCreatedAt(),
                        comment.getUser().getUsername()
                ))
                .toList();

        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt(),
                post.getTopic().getName(),
                post.getUser().getUsername(),
                comments
        );
    }

    @Transactional
    public void addComment(Long userId, Long postId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Cet article n'existe pas"));

        User user = userRepository.getReferenceById(userId);
        Comment comment = new Comment(user, post, request.getContent());
        commentRepository.save(comment);
    }
}
