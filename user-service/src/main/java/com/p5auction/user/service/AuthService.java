package com.p5auction.user.service;

import com.p5auction.user.dto.AuthDtos.*;
import com.p5auction.user.entity.UserAccount;
import com.p5auction.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final JwtEncoder encoder;
    private final String dummyHash = passwords.encode("invalid-user-placeholder");
    public AuthService(UserRepository users, @Value("${app.jwt-secret}") String secret) {
        this.users = users;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
    }
    @Transactional
    public UserView register(Register request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
        String email = normalize(request.email());
        if (users.findByEmail(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        return view(users.saveAndFlush(new UserAccount(email, request.displayName().strip(), passwords.encode(request.password()))));
    }
    @Transactional(readOnly = true)
    public TokenView login(Login request) {
        var account = users.findByEmail(normalize(request.email()));
        boolean matches = request.password().getBytes(StandardCharsets.UTF_8).length <= 72
            && passwords.matches(request.password(), account.map(u -> u.passwordHash).orElse(dummyHash));
        if (!matches || account.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("p5-user-service").audience(List.of("p5-services"))
            .subject(account.get().id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenView(token, "Bearer", 3600, view(account.get()));
    }
    public UserView me(UUID id) {
        return view(users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account unavailable")));
    }
    private static String normalize(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    private static UserView view(UserAccount user) { return new UserView(user.id, user.email, user.displayName); }
}
