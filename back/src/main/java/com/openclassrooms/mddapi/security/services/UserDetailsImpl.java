package com.openclassrooms.mddapi.security.services;

import com.openclassrooms.mddapi.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Adaptateur {@code UserDetails} par composition autour d'un {@code User}, utilisé
 * uniquement pendant le login (vérification du mot de passe par
 * {@code AuthenticationManager}). Il n'est jamais le principal d'une requête
 * authentifiée par JWT : là, le principal est le {@code Jwt} lui-même.
 * <p>
 * Invariants : {@link #getUsername()} renvoie le nom d'utilisateur, pas l'email, même
 * si le login a été fait par email ; {@link #getPassword()} renvoie le hash BCrypt ;
 * aucune autorité ; tous les indicateurs de compte (expiré, verrouillé, activé) sont
 * fixés à « valide » — le modèle ne porte aucun état de compte.
 */
public class UserDetailsImpl implements UserDetails {

    private final User user;

    public UserDetailsImpl(User user) {
        this.user = user;
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /** Id du compte, seule information reprise dans le claim {@code sub} du jeton émis au login. */
    public Long getId() {
        return user.getId();
    }
}