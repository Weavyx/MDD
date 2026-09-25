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

/**
 * Articles, fil d'actualité et commentaires.
 * <p>
 * Les articles et commentaires sont immuables une fois créés : aucune méthode de
 * modification ni de suppression n'existe, par choix de périmètre. L'auteur et la date
 * ne viennent jamais du client : l'auteur est l'id du JWT, la date est posée par la base.
 */
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

    /**
     * Fil de l'utilisateur : tous les articles des topics auxquels il est abonné, y compris
     * ceux d'autres auteurs, triés par date de création dans la direction demandée.
     * <p>
     * Non paginé : la liste entière est chargée en une requête (auteur et topic inclus par
     * {@code @EntityGraph}). Le contenu est tronqué à {@value #EXCERPT_MAX_LENGTH} caractères
     * suivis de « … » seulement s'il dépasse cette longueur ; en deçà il est renvoyé tel quel.
     * Un utilisateur sans abonnement, ou inexistant, obtient une liste vide, pas une erreur.
     */
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

    /**
     * Crée un article et renvoie son id (le contrôleur en fait l'en-tête {@code Location}).
     * <p>
     * Le topic est chargé pour produire un 404 fiable ; l'auteur est un proxy obtenu par
     * {@code getReferenceById} sans requête, l'id venant du JWT. Le contenu n'est pas borné
     * en longueur ({@code LONGTEXT}).
     *
     * @throws TopicNotFoundException si {@code request.topicId} ne correspond à aucun topic (404)
     */
    @Transactional
    public Long create(Long userId, CreatePostRequest request) {
        Topic topic = topicRepository.findById(request.getTopicId())
                .orElseThrow(() -> new TopicNotFoundException("Ce topic n'existe pas"));

        User user = userRepository.getReferenceById(userId);
        Post post = new Post(request.getTitle(), request.getContent(), user, topic);
        return postRepository.save(post).getId();
    }

    /**
     * Détail d'un article avec ses commentaires, du plus ancien au plus récent.
     * <p>
     * Accessible à tout utilisateur authentifié, abonné ou non au topic de l'article.
     * Deux requêtes fixes : l'article avec auteur et topic, puis les commentaires avec
     * leur auteur.
     *
     * @throws PostNotFoundException si l'article n'existe pas (404)
     */
    @Transactional(readOnly = true)
    public PostDetailResponse findById(Long postId) {
        Post post = postRepository.findWithUserAndTopicById(postId)
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

    /**
     * Ajoute un commentaire à un article ; les commentaires ne sont pas imbriqués.
     * <p>
     * L'article est chargé par {@code findById} sans {@code @EntityGraph} — seule son
     * existence compte, ni son auteur ni son topic ne sont lus. L'auteur du commentaire
     * est un proxy {@code getReferenceById}, sans requête.
     *
     * @throws PostNotFoundException si l'article n'existe pas (404)
     */
    @Transactional
    public void addComment(Long userId, Long postId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Cet article n'existe pas"));

        User user = userRepository.getReferenceById(userId);
        Comment comment = new Comment(user, post, request.getContent());
        commentRepository.save(comment);
    }
}
