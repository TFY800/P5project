package com.p5auction.user.controller;

import com.p5auction.user.dto.AuthDtos.*;
import com.p5auction.user.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public UserView register(@Valid @RequestBody Register request) { return auth.register(request); }
    @PostMapping("/auth/login")
    public TokenView login(@Valid @RequestBody Login request) { return auth.login(request); }
    @GetMapping("/users/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) { return auth.me(UUID.fromString(jwt.getSubject())); }
}
