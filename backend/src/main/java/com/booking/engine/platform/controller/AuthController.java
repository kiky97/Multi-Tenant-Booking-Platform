package com.booking.engine.platform.controller;

import com.booking.engine.entity.User;
import com.booking.engine.platform.repository.UserRepository;
import com.booking.engine.platform.security.JwtTokenService;
import com.booking.engine.platform.service.RedisLoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users;
    private final JwtTokenService tokens;
    private final RedisLoginRateLimiter loginRateLimiter;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository users, JwtTokenService tokens, RedisLoginRateLimiter loginRateLimiter) {
        this.users = users;
        this.tokens = tokens;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        loginRateLimiter.check(httpRequest.getRemoteAddr());
        User user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new LoginResponse(tokens.create(user), user.getRole().name());
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record LoginResponse(String accessToken, String role) {}
}
