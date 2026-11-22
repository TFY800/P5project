package com.p5auction.user.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;
public final class AuthDtos {
    private AuthDtos() {}
    public record Register(@NotBlank @Email @Size(max=254) String email,
                           @NotBlank @Size(min=10, max=72) String password,
                           @NotBlank @Size(max=80) String displayName) {
        public Register { if(email!=null) email=email.strip().toLowerCase(java.util.Locale.ROOT); }
    }
    public record Login(@NotBlank @Email @Size(max=254) String email, @NotBlank @Size(max=72) String password) {
        public Login { if(email!=null) email=email.strip().toLowerCase(java.util.Locale.ROOT); }
    }
    public record UserView(UUID id, String email, String displayName) {}
    public record TokenView(String accessToken, String tokenType, long expiresIn, UserView user) {}
}
