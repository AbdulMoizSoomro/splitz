package com.splitz.user.service;

import com.splitz.user.dto.RoleDTO;
import com.splitz.user.mapper.RoleMapper;
import com.splitz.user.model.Role;
import com.splitz.user.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoleService {

  @Autowired private final RoleRepository roleRepository;
  @Autowired private final RoleMapper roleMapper;

  public RoleService(RoleRepository roleRepository, RoleMapper roleMapper) {
    this.roleRepository = roleRepository;
    this.roleMapper = roleMapper;
  }

  public List<RoleDTO> getAllRoles() {
    return roleRepository.findAll().stream().map(roleMapper::toDTO).collect(Collectors.toList());
  }

  public Optional<RoleDTO> getRoleById(long id) {
    return roleRepository.findById(id).map(roleMapper::toDTO);
  }

  public Optional<RoleDTO> getRoleByName(String name) {
    return roleRepository.findByName(name).map(roleMapper::toDTO);
  }

  public RoleDTO createRole(String name) {
    Optional<Role> existingRole = roleRepository.findByName(name);
    if (existingRole.isPresent()) {
      throw new IllegalArgumentException("Role with name " + name + "exists.");
    }
    Role newRole = new Role(name);
    Role savedRole = roleRepository.save(newRole);
    return roleMapper.toDTO(savedRole);
  }

  public void deleteRole(long id) {
    if (!roleRepository.existsById(id)) {
      throw new IllegalArgumentException("Role with ID '" + id + "' not found.");
    }
    roleRepository.deleteById(id);
  }
}
