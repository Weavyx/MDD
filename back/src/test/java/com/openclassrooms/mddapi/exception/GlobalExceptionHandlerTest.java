package com.openclassrooms.mddapi.exception;

import com.openclassrooms.mddapi.dto.ErrorResponse;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.hibernate.exception.DataException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void violationDIntegrite_doublonDEmail_renvoie409EtJournaliseLaContrainteSansLaValeur(CapturedOutput output) {
        // Arrange : chaîne d'exceptions d'un doublon d'e-mail sous MySQL, valeur comprise dans les messages
        SQLIntegrityConstraintViolationException cause = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'doublon@example.com' for key 'users.uk_users_email'", "23000", 1062);
        ConstraintViolationException violationHibernate = new ConstraintViolationException(
                cause.getMessage(), cause, ConstraintKind.UNIQUE, "users.uk_users_email");
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'doublon@example.com' for key 'users.uk_users_email']",
                violationHibernate);
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // Act
        ResponseEntity<ErrorResponse> reponse = handler.handleDataIntegrityViolation(exception);

        // Assert
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reponse.getBody()).extracting(ErrorResponse::getMessage).isEqualTo("La ressource entre en conflit avec une contrainte existante");
        assertThat(output.getAll().lines().filter(ligne -> ligne.contains("Violation de contrainte : users.uk_users_email (UNIQUE)")))
                .singleElement().asString().contains(" WARN ");
        assertThat(output.getAll()).doesNotContain("doublon@example.com");
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void violationDIntegrite_causeSansContrainte_journaliseUneContrainteInconnue(CapturedOutput output) {
        // Arrange : cause Hibernate qui n'est pas une violation de contrainte ; messages inventés pour ce test (pas un texte réel de MySQL), porteurs d'une valeur reçue
        String valeurRecue = "valeur-recue-contrainte-inconnue";
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "could not execute statement [valeur refusée : '" + valeurRecue + "']",
                new DataException("valeur refusée : '" + valeurRecue + "'",
                        new SQLException("valeur refusée : '" + valeurRecue + "'", "22001", 1406)));
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        // Act
        ResponseEntity<ErrorResponse> reponse = handler.handleDataIntegrityViolation(exception);

        // Assert
        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(output.getAll().lines().filter(ligne -> ligne.contains("Violation de contrainte : inconnue")))
                .singleElement().asString().contains(" WARN ");
        assertThat(output.getAll()).doesNotContain(valeurRecue);
    }
}
