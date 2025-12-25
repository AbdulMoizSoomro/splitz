package com.splitz.user.security;

import com.splitz.user.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }


    @PostMapping("/authenticate")
    public ResponseEntity<JwtResponse> authenticate(@RequestBody JwtRequest request) throws Exception {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (Exception ex) {
            throw new Exception("Invalid username/password"); // Consider more specific exception
        }

        final UserDetails userDetails = userService.loadUserByUsername(request.username());
        final String jwt = jwtUtil.generateToken(userDetails);
        return ResponseEntity.ok(new JwtResponse(jwt));
    }

    public record JwtRequest(String username, String password) {}
    public record JwtResponse(String token) {}

}
