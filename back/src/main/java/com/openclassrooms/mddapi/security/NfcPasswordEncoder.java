package com.openclassrooms.mddapi.security;

import java.text.Normalizer;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Normalise le mot de passe en Unicode NFC avant de le déléguer à l'encodeur réel.
 * Un même mot de passe saisi sous deux formes Unicode (« é » précomposé ou « e » + accent combinant)
 * produit ainsi le même hash, comme le recommande NIST SP 800-63B-4.
 */
public final class NfcPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;

    public NfcPasswordEncoder(PasswordEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(normalize(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return delegate.matches(normalize(rawPassword), encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    private static String normalize(CharSequence rawPassword) {
        return rawPassword == null ? null : Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
    }
}
