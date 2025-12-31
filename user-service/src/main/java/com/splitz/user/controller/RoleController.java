package com.splitz.user.controller;

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

import com.splitz.user.dto.CreateRoleRequest;
import com.splitz.user.dto.RoleDTO;
import com.splitz.user.service.RoleService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/roles")
@PreAuthorize("hasRole('ADMIN')") // All role management requires ADMIN
public class RoleController {

    @Autowired
    private RoleService roleService;

    // Get role by ID - numeric IDs only
    @GetMapping("/id/{id}")
    public ResponseEntity<RoleDTO> getById(@PathVariable Long id) {
        return roleService.getRoleById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get role by name - using query parameter to avoid ambiguity
    @GetMapping("/search")
    public ResponseEntity<RoleDTO> getByName(@RequestParam String name) {
        return roleService.getRoleByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Get all roles
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
