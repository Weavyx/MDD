package com.openclassrooms.mddapi.security.services;

import com.openclassrooms.mddapi.model.User;
import com.openclassrooms.mddapi.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Résout un compte par email <em>ou</em> nom d'utilisateur : le paramètre est
     * l'{@code identifier} unique du formulaire de connexion, comparé aux deux colonnes
     * en une seule requête. Les deux colonnes étant {@code UNIQUE}, au plus une ligne
     * correspond — sauf si un nom d'utilisateur est égal à l'email d'un autre compte,
     * cas non interdit par le modèle qui ferait échouer la requête.
     *
     * @throws UsernameNotFoundException si aucun compte ne correspond ; convertie par
     *         Spring Security en {@code BadCredentialsException}, donc indistinguable d'un
     *         mauvais mot de passe côté client (401)
     */
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByEmailOrUsername(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
        return new UserDetailsImpl(user);
    }
}
