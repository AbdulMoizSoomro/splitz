package com.splitz.user.security;

import com.splitz.security.JwtUtil;
import com.splitz.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Authentication", description = "Endpoints for user login and token generation")
public class AuthController {
  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final JwtUtil jwtUtil;

  public AuthController(
      UserService userService, AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
    this.userService = userService;
    this.authenticationManager = authenticationManager;
    this.jwtUtil = jwtUtil;
  }

  @Operation(
      summary = "Authenticate user",
      description = "Authenticates a user with username and password and returns a JWT token.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully authenticated",
            content = @Content(schema = @Schema(implementation = JwtResponse.class))),
        @ApiResponse(responseCode = "401", description = "Invalid credentials", content = @Content)
      })
  @PostMapping("/authenticate")
  public ResponseEntity<JwtResponse> authenticate(@RequestBody JwtRequest request)
      throws Exception {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.username(), request.password()));
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
