package com.splitz.user.service;

import com.splitz.user.dto.UserDTO;
import com.splitz.user.exception.UserAlreadyExistsException;
import com.splitz.user.mapper.UserMapper;
import com.splitz.user.model.User;
import com.splitz.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private final UserRepository userRepository;
    @Autowired
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public List<User> getAllUsers(){
        return userRepository.findAll();
    }

    public Optional<User> getUserbyId(Long id){
        return userRepository.findById(id);
    }


    public Optional<User> findByusername(String username) throws UsernameNotFoundException {
        return userRepository.findByusername(username);
    }

    public UserDTO createUser(UserDTO newUserDTO){
        if (userRepository.findByusername(newUserDTO.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException(newUserDTO.getUsername());
        }
        User user = userMapper.toEntity(newUserDTO);
        return userMapper.toDTO(userRepository.save(user));
    }


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> optionalUser = userRepository.findByusername(username);
        return optionalUser.orElseThrow(() -> new UsernameNotFoundException("User not found" + username));
    }
}
