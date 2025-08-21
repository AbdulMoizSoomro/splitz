package com.splitz.user.service;

import com.splitz.user.model.Role;
import com.splitz.user.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoleService {

    @Autowired
    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Optional<Role> getRoleById(Long id) {
        return roleRepository.findById(id);
    }

    public Optional<Role> getRoleByName(String name) {
        return roleRepository.findByName(name);
    }

    public Role createRole(String name){
        Optional<Role> existingRole = roleRepository.findByName(name);
        if(existingRole.isPresent()){
            throw new IllegalArgumentException("Role with name "+ name +"exists." );
        }
        Role newRole = new Role(name);
        return roleRepository.save(newRole);
    }

    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new IllegalArgumentException("Role with ID '" + id + "' not found.");
        }
        roleRepository.deleteById(id);
    }
}
