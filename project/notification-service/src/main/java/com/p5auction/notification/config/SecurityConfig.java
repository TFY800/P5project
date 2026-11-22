package com.p5auction.notification.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${app.jwt-secret}") String secret) {
        if (secret.startsWith("REPLACE_") || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        var decoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidatorAdapter());
        return decoder;
    }
    // Explicit issuer, audience and zero clock skew: expiry is enforced at server time.
    private static class DelegatingOAuth2TokenValidatorAdapter implements org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> {
        private final org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> delegate =
            new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(java.time.Duration.ZERO), new JwtIssuerValidator("p5-user-service"),
                new JwtClaimValidator<java.time.Instant>("exp", java.util.Objects::nonNull),
                new JwtClaimValidator<java.util.List<String>>("aud", aud -> aud != null && aud.contains("p5-services")),
                new JwtClaimValidator<String>("sub", sub -> { try { java.util.UUID.fromString(sub); return true; } catch (Exception e) { return false; } }));
        public org.springframework.security.oauth2.core.OAuth2TokenValidatorResult validate(Jwt token) { return delegate.validate(token); }
    }
    @Bean
    SecurityFilterChain chain(HttpSecurity http, @Value("${app.internal-secret}") String internalSecret,
                              @Value("${app.cors-origin}") String origin, @Value("${app.jwt-secret}") String jwtSecret) throws Exception {
        if (internalSecret.startsWith("REPLACE_") || internalSecret.equals(jwtSecret) || internalSecret.length() < 32) throw new IllegalArgumentException("INTERNAL_SECRET must be a distinct random secret of at least 32 characters");
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", cors);
        var internal = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws ServletException, IOException {
                if (request.getRequestURI().startsWith("/internal/")) {
                    String supplied = request.getHeader("X-Internal-Key");
                    if (supplied == null || !MessageDigest.isEqual(internalSecret.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                        problem(response, 401, "Internal authentication required"); return;
                    }
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        "auction-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
                }
                chain.doFilter(request, response);
            }
        };
        return http.cors(c -> c.configurationSource(source)).csrf(c -> c.disable())
            .sessionManagement(c -> c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c -> c
                .requestMatchers("/internal/**").hasRole("INTERNAL")
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/health", "/api/auth/register", "/api/auth/login", "/ws").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/items/mine", "/api/auctions/mine", "/api/auctions/participated", "/api/auctions/won").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/items/**", "/api/auctions/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(c -> c.jwt(j -> {}).authenticationEntryPoint((req, res, e) -> problem(res, 401, "Valid login required")))
            .exceptionHandling(c -> c.authenticationEntryPoint((req, res, e) -> problem(res, 401, "Valid login required"))
                .accessDeniedHandler((req, res, e) -> problem(res, 403, "Access denied")))
            .addFilterBefore(internal, BearerTokenAuthenticationFilter.class).build();
    }
    private static void problem(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
