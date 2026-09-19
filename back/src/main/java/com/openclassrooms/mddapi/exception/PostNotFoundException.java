package com.openclassrooms.mddapi.exception;

/**
 * Levée par {@code PostService.findById} et {@code addComment} quand l'id d'article ne correspond à rien ; traduite en 404. Un id non numérique ne l'atteint pas : il produit un 400 en amont.
 */
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(String message) {
        super(message);
    }
}
