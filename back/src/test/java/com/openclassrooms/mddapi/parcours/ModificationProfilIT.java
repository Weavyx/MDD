package com.openclassrooms.mddapi.parcours;

import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Action de la spécification : « Modifier son profil (e-mail, nom d'utilisateur et mot de
 * passe) via la page de profil ». Route : {@code PATCH /api/user}.
 * <p>
 * Mot de passe : clé absente = inchangé ; vide, blanc ou non conforme = 400.
 */
class ModificationProfilIT extends ParcoursIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Act commun : envoie {@code corps} à la route de modification du profil. */
    private ResultActions modifierProfil(String jeton, String corps) throws Exception {
        return mockMvc.perform(patch("/api/user")
                .header("Authorization", "Bearer " + jeton)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corps));
    }

    /** Corps de {@code PATCH /api/user} ; {@code motDePasse} null = clé absente. */
    private static String corpsProfil(String username, String email, String motDePasse) {
        String debut = "{\"username\":\"" + username + "\",\"email\":\"" + email + "\"";
        return motDePasse == null ? debut + "}" : debut + ",\"password\":\"" + motDePasse + "\"}";
    }

    /** Relecture en base d'un compte par e-mail ou nom d'utilisateur. */
    private User compte(String identifiant) {
        return userRepository.findByEmailOrUsername(identifiant).orElseThrow();
    }

    @Test
    void modification_emailEtNom_retourne200EtEnregistreLesNouvellesValeurs() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("profil-avant");
        String corps = corpsProfil("profil-apres", "profil-apres@mail.com", null);

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("profil-apres"))
                .andExpect(jsonPath("$.email").value("profil-apres@mail.com"));
        viderLeCache();
        assertThat(compte("profil-apres").getEmail()).isEqualTo("profil-apres@mail.com");
        assertThat(userRepository.findByEmailOrUsername("profil-avant@mail.com")).isEmpty();
    }

    @Test
    void modification_nouveauMotDePasse_remplaceLeHash() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("profil-mdp");
        String corps = corpsProfil("profil-mdp", "profil-mdp@mail.com", "NouveauMdp1!");

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        viderLeCache();
        String hash = compte("profil-mdp").getPasswordHash();
        assertThat(passwordEncoder.matches("NouveauMdp1!", hash)).isTrue();
        assertThat(passwordEncoder.matches(MOT_DE_PASSE, hash)).isFalse();
    }

    @Test
    void modification_sansMotDePasse_conserveLeHash() throws Exception {
        // Arrange
        String jeton = inscrireEtConnecter("profil-garde");
        String hashAvant = compte("profil-garde").getPasswordHash();
        String corps = corpsProfil("profil-garde-2", "profil-garde@mail.com", null);

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isOk());
        viderLeCache();
        assertThat(compte("profil-garde-2").getPasswordHash()).isEqualTo(hashAvant);
    }

    @Test
    void modification_emailPrisParUnAutreCompte_retourne409() throws Exception {
        // Arrange
        inscrire("profil-autre");
        String jeton = inscrireEtConnecter("profil-moi");
        String corps = corpsProfil("profil-moi", "profil-autre@mail.com", null);

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cet email est déjà utilisé"));
        viderLeCache();
        assertThat(compte("profil-moi").getEmail()).isEqualTo("profil-moi@mail.com");
    }

    @Test
    void modification_nomPrisParUnAutreCompte_retourne409() throws Exception {
        // Arrange
        inscrire("profil-autre-nom");
        String jeton = inscrireEtConnecter("profil-moi-nom");
        String corps = corpsProfil("profil-autre-nom", "profil-moi-nom@mail.com", null);

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ce nom d'utilisateur est déjà utilisé"));
        viderLeCache();
        assertThat(compte("profil-moi-nom@mail.com").getUsername()).isEqualTo("profil-moi-nom");
    }

    @Test
    void modification_casseDeSesPropresEmailEtNom_retourne200() throws Exception {
        // Arrange : la base compare sans tenir compte de la casse (utf8mb4_0900_ai_ci) ;
        // le compte ne doit pas entrer en conflit avec lui-même
        String jeton = inscrireEtConnecter("profil-casse");
        String corps = corpsProfil("Profil-Casse", "Profil-Casse@mail.com", null);

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Profil-Casse"))
                .andExpect(jsonPath("$.email").value("Profil-Casse@mail.com"));
    }

    @ParameterizedTest(name = "mot de passe refusé : \"{0}\"")
    @ValueSource(strings = {"", "   ", "Password1"})
    void modification_motDePasseInvalide_retourne400(String motDePasse) throws Exception {
        // Arrange : clé présente mais vide, blanche ou sans caractère spécial
        String jeton = inscrireEtConnecter("profil-mdp-invalide");
        String corps = corpsProfil("profil-mdp-invalide", "profil-mdp-invalide@mail.com", motDePasse);

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void modification_motDePasseDe74Octets_retourne400() throws Exception {
        // Arrange : 10 caractères ASCII + 32 « é » de 2 octets = 74 octets, mais 42 caractères
        String jeton = inscrireEtConnecter("profil-mdp-long");
        String corps = corpsProfil("profil-mdp-long", "profil-mdp-long@mail.com", MOT_DE_PASSE + "é".repeat(32));

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").value(containsString("72 octets")));
    }

    @ParameterizedTest(name = "{index} : {0} refusé")
    @CsvSource(delimiter = '|', textBlock = """
            email    | {"username":"profil-invalide","email":"pas-un-email"}
            username | {"username":"a@b.fr","email":"profil-invalide@mail.com"}
            email    | {"username":"profil-invalide"}
            username | {"email":"profil-invalide@mail.com"}
            """)
    void modification_donneesInvalides_retourne400(String champ, String corps) throws Exception {
        // Arrange : corps fourni par @CsvSource, un champ invalide ou absent par cas
        String jeton = inscrireEtConnecter("profil-invalide");

        // Act
        ResultActions reponse = modifierProfil(jeton, corps);

        // Assert
        reponse.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.%s", champ).exists());
    }
}
