package com.openclassrooms.mddapi.exception;

import com.openclassrooms.mddapi.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduction des exceptions en réponses {@code ErrorResponse}, seul point de mapping
 * exception → statut HTTP de l'application.
 * <p>
 * Périmètre volontairement borné : les erreurs de validation, les six exceptions métier,
 * les violations de contrainte en base et les paramètres d'URL invalides. Ne sont
 * <em>pas</em> traités ici, et sortent donc du format {@code ErrorResponse} :
 * <ul>
 *   <li>les 401 (jeton absent, invalide, expiré, identifiants faux), rendus par le point
 *       d'entrée de Spring Security avec un corps vide ;</li>
 *   <li>le JSON malformé ({@code HttpMessageNotReadableException}), rendu par le
 *       mécanisme d'erreur par défaut de Spring MVC ;</li>
 *   <li>toute autre exception (500) : il n'y a pas de handler {@code Exception.class},
 *       par choix — un attrape-tout transformerait les 401 en 500.</li>
 * </ul>
 * Aucun message ne reprend une valeur saisie par le client ni un détail SQL.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 400 avec {@code fieldErrors} : clé = nom du champ du DTO, valeur = {@code message}
     * de l'annotation violée. Une seule entrée par champ : si deux contraintes échouent
     * sur le même champ (ex. {@code @Size} et {@code @Pattern} du mot de passe), seule
     * la dernière parcourue est conservée. La valeur rejetée n'est jamais renvoyée.
     * Seul handler à renseigner {@code fieldErrors} ; ailleurs il vaut {@code null}.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                new ErrorResponse(Instant.now(), 400, "Bad Request", "Requête invalide", fieldErrors)
        );
    }

    @ExceptionHandler(TopicNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTopicNotFound(TopicNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(PostNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePostNotFound(PostNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(AlreadySubscribedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadySubscribed(AlreadySubscribedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyUsed(EmailAlreadyUsedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(UsernameAlreadyUsedException.class)
    public ResponseEntity<ErrorResponse> handleUsernameAlreadyUsed(UsernameAlreadyUsedException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * 409 à message fixe pour toute contrainte violée en base : filet des courses que les
     * vérifications applicatives ne couvrent pas (double abonnement simultané, inscription
     * concurrente avec le même email) et des clés étrangères ({@code getReferenceById} sur
     * un compte supprimé). Le message de l'exception, qui contiendrait le nom de la
     * contrainte et le SQL, n'est pas transmis.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT, "La ressource entre en conflit avec une contrainte existante");
    }

    /**
     * 400 pour un segment d'URL non convertible (ex. {@code /api/posts/abc}). Le message
     * est générique et ne cite ni le paramètre ni la valeur reçue.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Le paramètre fourni est invalide");
    }

    /**
     * 400 pour une contrainte posée directement sur un paramètre de contrôleur — dans ce
     * projet, uniquement {@code @Pattern("asc|desc")} sur {@code sort} du fil. Contrairement
     * aux erreurs de corps, aucun {@code fieldErrors} n'est renseigné.
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Le paramètre fourni est invalide");
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message) {
        ErrorResponse body = new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, null);
        return ResponseEntity.status(status).body(body);
    }
}
