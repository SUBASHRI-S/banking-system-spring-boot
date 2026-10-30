package com.banksim.auth;

import com.banksim.auth.dto.LoginRequest;
import com.banksim.auth.dto.LoginResponse;
import com.banksim.auth.dto.RegisterRequest;
import com.banksim.auth.dto.RegisterResponse;
import com.banksim.common.DuplicateEmailException;
import com.banksim.common.UnauthorizedException;
import com.banksim.user.Role;
import com.banksim.user.User;
import com.banksim.user.UserRepository;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    /** Compared against when the email is unknown, so "no such user" and "wrong password" take similar time. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.dummyHash = encoder.encode("not-a-real-password");
    }

    /** BANK-FR-01. Public registration always creates a USER; admins are created by AdminBootstrap. */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        try {
            User saved = users.saveAndFlush(new User(email, encoder.encode(request.password()),
                    request.fullName().trim(), Role.USER));
            return new RegisterResponse(saved.getId(), saved.getEmail(), saved.getRole());
        } catch (DataIntegrityViolationException e) {
            // two registrations with the same email at the same moment: the unique index wins
            throw new DuplicateEmailException();
        }
    }

    /** BANK-FR-02. The error never says whether the email or the password was wrong. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmail(normalize(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean matches = encoder.matches(request.password(), hash);
        if (user == null || !matches) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return new LoginResponse(jwt.issue(user), "Bearer", jwt.expirySeconds());
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
