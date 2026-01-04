package com.splitz.user.controller;

import com.splitz.user.dto.UpdateUserDTO;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Endpoints for user management and search")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  // Create a new user (public endpoint for registration)
  @Operation(
      summary = "Register a new user",
      description = "Creates a new user account. This is a public endpoint.",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "User successfully created",
            content = @Content(schema = @Schema(implementation = UserDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content),
        @ApiResponse(responseCode = "409", description = "User already exists", content = @Content)
      })
  @PostMapping
  @PreAuthorize("permitAll()")
  public ResponseEntity<UserDTO> createUser(@Valid @RequestBody UserDTO userDTO) {
    return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(userDTO));
  }

  // Get all users - ADMIN only (contains sensitive email data)
  @Operation(
      summary = "List all users",
      description = "Returns a list of all registered users. Restricted to ADMIN role.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required",
            content = @Content)
      })
  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<List<UserDTO>> getAllUsers() {
    List<UserDTO> users = userService.getAllUsers();
    return ResponseEntity.ok(users);
  }

  // Get user by ID - authenticated users only
  @Operation(
      summary = "Get user by ID",
      description = "Returns details of a specific user by their ID. Requires authentication.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "User found",
            content = @Content(schema = @Schema(implementation = UserDTO.class))),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
      })
  @GetMapping("/{id}")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<UserDTO> getUserById(
      @Parameter(description = "ID of the user to retrieve") @PathVariable("id") Long id) {
    Optional<UserDTO> user = userService.getUserbyId(id);
    return user.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  // Update user - only owner or admin
  @Operation(
      summary = "Update user",
      description =
          "Updates an existing user's details. Only the user themselves or an ADMIN can perform this action.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "User successfully updated",
            content = @Content(schema = @Schema(implementation = UserDTO.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Not owner or admin",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
      })
  @PutMapping("/{id}")
  @PreAuthorize("@security.isOwnerOrAdmin(#id)")
  public ResponseEntity<UserDTO> updateUser(
      @Parameter(description = "ID of the user to update") @PathVariable("id") Long id,
      @Valid @RequestBody UpdateUserDTO updateDTO) {
    UserDTO updatedUser = userService.updateUser(id, updateDTO);
    return ResponseEntity.ok(updatedUser);
  }

  // Delete user - only owner or admin
  @Operation(
      summary = "Delete user",
      description =
          "Deletes a user account. Only the user themselves or an ADMIN can perform this action.",
      responses = {
        @ApiResponse(responseCode = "204", description = "User successfully deleted"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Not owner or admin",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
      })
  @DeleteMapping("/{id}")
  @PreAuthorize("@security.isOwnerOrAdmin(#id)")
  public ResponseEntity<Void> deleteUser(
      @Parameter(description = "ID of the user to delete") @PathVariable("id") Long id) {
    userService.deleteUser(id);
    return ResponseEntity.noContent().build();
  }

  // Search users by name/email with pagination - authenticated users only
  @Operation(
      summary = "Search users",
      description = "Searches for users by name or email with pagination. Requires authentication.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
      })
  @GetMapping("/search")
  @PreAuthorize("isAuthenticated()")
  public ResponseEntity<Page<UserDTO>> searchUsers(
      @Parameter(description = "Search query (name or email)") @RequestParam("query") String query,
      @Parameter(description = "Page number (0-indexed)")
          @RequestParam(name = "page", defaultValue = "0")
          int page,
      @Parameter(description = "Page size") @RequestParam(name = "size", defaultValue = "10")
          int size) {
    Pageable pageable = PageRequest.of(page, size);
    Page<UserDTO> users = userService.searchUsers(query, pageable);
    return ResponseEntity.ok(users);
  }
}
