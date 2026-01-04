package com.splitz.user.controller;

import com.splitz.user.dto.CreateRoleRequest;
import com.splitz.user.dto.RoleDTO;
import com.splitz.user.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/roles")
@PreAuthorize("hasRole('ADMIN')") // All role management requires ADMIN
@Tag(name = "Roles", description = "Endpoints for managing user roles. Restricted to ADMIN role.")
public class RoleController {

  @Autowired private RoleService roleService;

  // Get role by ID - numeric IDs only
  @Operation(
      summary = "Get role by ID",
      description = "Returns details of a specific role by its ID. Restricted to ADMIN role.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Role found",
            content = @Content(schema = @Schema(implementation = RoleDTO.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Role not found", content = @Content)
      })
  @GetMapping("/id/{id}")
  public ResponseEntity<RoleDTO> getById(
      @Parameter(description = "ID of the role to retrieve") @PathVariable Long id) {
    return roleService
        .getRoleById(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  // Get role by name - using query parameter to avoid ambiguity
  @Operation(
      summary = "Get role by name",
      description = "Returns details of a specific role by its name. Restricted to ADMIN role.",
      responses = {
        @ApiResponse(
            responseCode = "200",
            description = "Role found",
            content = @Content(schema = @Schema(implementation = RoleDTO.class))),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required",
            content = @Content),
        @ApiResponse(responseCode = "404", description = "Role not found", content = @Content)
      })
  @GetMapping("/search")
  public ResponseEntity<RoleDTO> getByName(
      @Parameter(description = "Name of the role to retrieve") @RequestParam String name) {
    return roleService
        .getRoleByName(name)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  // Get all roles
  @Operation(
      summary = "List all roles",
      description = "Returns a list of all available roles. Restricted to ADMIN role.",
      responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required",
            content = @Content)
      })
  @GetMapping
  public ResponseEntity<List<RoleDTO>> getAllRoles() {
    try {
      List<RoleDTO> roles = roleService.getAllRoles();
      return ResponseEntity.ok(roles);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }

  // Create new role with proper DTO and validation
  @Operation(
      summary = "Create a new role",
      description = "Creates a new user role. Restricted to ADMIN role.",
      responses = {
        @ApiResponse(
            responseCode = "201",
            description = "Role successfully created",
            content = @Content(schema = @Schema(implementation = RoleDTO.class))),
        @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content),
        @ApiResponse(
            responseCode = "403",
            description = "Forbidden - Admin role required",
            content = @Content),
        @ApiResponse(responseCode = "409", description = "Role already exists", content = @Content)
      })
  @PostMapping
  public ResponseEntity<RoleDTO> createRole(@Valid @RequestBody CreateRoleRequest request) {
    try {
      RoleDTO newRole = roleService.createRole(request.getName());
      return ResponseEntity.status(HttpStatus.CREATED).body(newRole);
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }
}
