package com.openclassrooms.mddapi.service;

import com.openclassrooms.mddapi.dto.AuthResponse;
import com.openclassrooms.mddapi.dto.LoginRequest;
import com.openclassrooms.mddapi.dto.RegisterRequest;
import com.openclassrooms.mddapi.exception.EmailAlreadyUsedException;
import com.openclassrooms.mddapi.exception.UsernameAlreadyUsedException;
import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import com.openclassrooms.mddapi.security.jwt.JwtService;
import com.openclassrooms.mddapi.security.services.UserDetailsImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Inscription et connexion ; seul point d'émission des JWT.
 * <p>
 * Le jeton émis porte l'id numérique de l'utilisateur dans {@code sub}, jamais son email
 * ni son nom d'utilisateur : ces deux valeurs sont modifiables via le profil sans que le
 * jeton soit invalidé. Aucune de ces méthodes n'est transactionnelle.
 */
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /**
     * Crée le compte et renvoie directement un jeton : l'inscription vaut connexion.
     * <p>
     * L'email est vérifié avant le nom d'utilisateur ; si les deux sont pris, seule
     * l'erreur d'email est signalée. Le mot de passe est haché avec BCrypt avant
     * l'enregistrement, jamais stocké en clair. Les deux vérifications d'unicité et
     * l'enregistrement ne sont pas atomiques : deux inscriptions concurrentes avec le
     * même email sont départagées par la contrainte {@code UNIQUE} en base (409 générique).
     * Un mot de passe {@code null} n'est pas rejeté par ce service : la garantie repose
     * sur le {@code @NotBlank} de {@code RegisterRequest.password}. Sans lui,
     * {@code BCryptPasswordEncoder.encode(null)} renvoie {@code null} et l'insertion
     * échoue sur {@code password_hash NOT NULL} en 409 trompeur (revue technique, axe p).
     *
     * @throws EmailAlreadyUsedException    si l'email est déjà enregistré (409)
     * @throws UsernameAlreadyUsedException si le nom d'utilisateur est déjà enregistré (409)
     */
    public AuthResponse register(RegisterRequest request) {
        String requestEmail = request.getEmail();
        String requestUsername = request.getUsername();

        if(userRepository.existsByEmail(requestEmail)){
            throw new EmailAlreadyUsedException("Cet email est déjà utilisé");
        }
        if(userRepository.existsByUsername(requestUsername)){
            throw new UsernameAlreadyUsedException("Ce nom d'utilisateur est déjà utilisé");
        }

        User user = new User();
            user.setEmail(requestEmail);
            user.setUsername(requestUsername);
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        User savedUser = userRepository.save(user);
        String id = savedUser.getId().toString();

        return new AuthResponse(jwtService.generateToken(id));
    }

    /**
     * Authentifie par email <em>ou</em> nom d'utilisateur (un seul champ {@code identifier})
     * et renvoie un jeton portant l'id du compte.
     * <p>
     * L'échec n'est pas traité ici : {@code AuthenticationManager} lève une
     * {@code BadCredentialsException} (identifiant inconnu ou mot de passe faux, sans
     * distinction) qui remonte jusqu'à Spring Security et devient un 401 à corps vide,
     * hors du format {@code ErrorResponse} — comportement volontairement non redéfini,
     * figé par {@code AuthControllerIT.login_identifiantsInvalides_retourne401}.
     */
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getIdentifier(), request.getPassword())
        );

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return new AuthResponse(jwtService.generateToken(userDetails.getId().toString()));
    }

}
