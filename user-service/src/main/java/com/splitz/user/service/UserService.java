package com.splitz.user.service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.splitz.user.dto.UserDTO;
import com.splitz.user.exception.ResourceNotFoundException;
import com.splitz.user.exception.UserAlreadyExistsException;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final RoleRepository roleRepository;
    @Autowired
    private final UserMapper userMapper;
    @Autowired
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, UserMapper userMapper,
            BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    public Optional<UserDTO> getUserbyId(long id) {
        return userRepository.findById(id).map(userMapper::toDTO);
    }

    public Optional<User> findByusername(String username) throws UsernameNotFoundException {
        return userRepository.findByusername(username);
    }

    public UserDTO createUser(UserDTO newUserDTO) {
        // Check if username already exists
        if (userRepository.findByusername(newUserDTO.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username already exists: " + newUserDTO.getUsername());
        }

        // Check if email already exists
        Optional<User> existingEmail = userRepository.findByEmail(newUserDTO.getEmail());
        if (existingEmail.isPresent()) {
            throw new UserAlreadyExistsException("Email already exists: " + newUserDTO.getEmail());
        }

        // Encode password and create user
        User user = userMapper.toEntityWithPasswordEncoding(newUserDTO, passwordEncoder);

        // Assign default role
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
        user.setRoles(Collections.singleton(userRole));

        User savedUser = userRepository.save(user);
        return userMapper.toDTO(savedUser);
    }

    public UserDTO updateUser(Long id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        // Update only provided fields (null-safe partial update)
        if (userDTO.getFirstName() != null && !userDTO.getFirstName().isBlank()) {
            user.setFirstName(userDTO.getFirstName());
        }
        if (userDTO.getLastName() != null && !userDTO.getLastName().isBlank()) {
            user.setLastName(userDTO.getLastName());
        }
        if (userDTO.getPassword() != null && !userDTO.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toDTO(updatedUser);
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    public Page<UserDTO> searchUsers(String query, Pageable pageable) {
        return userRepository.searchByUsernameOrEmailOrFirstName(query, pageable)
                .map(userMapper::toDTO);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> optionalUser = userRepository.findByusername(username);
        return optionalUser.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
