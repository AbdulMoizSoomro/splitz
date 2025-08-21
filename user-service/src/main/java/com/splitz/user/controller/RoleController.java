package com.splitz.user.controller;

import com.splitz.user.model.Role;
import com.splitz.user.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/public/role")
public class RoleController {

    @Autowired
    private RoleService roleService;

    @GetMapping("/{id}")
    public Optional<Role> getById(@PathVariable Long id){
        return roleService.getRoleById(id);
    }

    @GetMapping("/{name}")
    public Optional<Role> getByName(@PathVariable String name){
        return roleService.getRoleByName(name);
    }

    @GetMapping("getAllR")
    public  ResponseEntity<List<Role>> getAllRoles(){
        try{
            List<Role> roles = roleService.getAllRoles();
            return new ResponseEntity<List<Role>>(roles, HttpStatus.OK);
        }
        catch (IllegalArgumentException e) {
            return new ResponseEntity<List<Role>>(HttpStatus.CONFLICT);
        }
    }

    @PostMapping
    public ResponseEntity<Role> createRole(@RequestBody String name) {
        try {
            Role newRole = roleService.createRole(name);
            return new ResponseEntity<>(newRole, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(HttpStatus.CONFLICT); // Or HttpStatus.BAD_REQUEST
        }
    }
}
