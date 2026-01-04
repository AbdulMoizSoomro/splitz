package com.splitz.user.mapper;

import com.splitz.user.dto.UserDTO;
import com.splitz.user.model.User;
import org.mapstruct.Mapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Mapper(componentModel = "spring")
public interface UserMapper {

  default UserDTO toDTO(User user) {
    if (user == null) {
      return null;
    }
    UserDTO dto = new UserDTO();
    dto.setId(user.getId());
    dto.setUsername(user.getUsername());
    dto.setEmail(user.getEmail());
    dto.setFirstName(user.getFirstName());
    dto.setLastName(user.getLastName());
    dto.setPassword(null); // Never return password
    return dto;
  }

  default User toEntity(UserDTO userDTO) {
    if (userDTO == null) {
      return null;
    }
    User user = new User();
    user.setId(userDTO.getId());
    user.setUsername(userDTO.getUsername());
    user.setEmail(userDTO.getEmail());
    user.setFirstName(userDTO.getFirstName());
    user.setLastName(userDTO.getLastName());
    return user;
  }

  default User toEntityWithPasswordEncoding(
      UserDTO userDTO, BCryptPasswordEncoder passwordEncoder) {
    if (userDTO == null) {
      return null;
    }

    User user = new User();
    user.setId(userDTO.getId());
    user.setUsername(userDTO.getUsername());
    user.setEmail(userDTO.getEmail());
    user.setFirstName(userDTO.getFirstName());
    user.setLastName(userDTO.getLastName());
    user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
    user.setEnabled(true);
    user.setVerified(true); // For MVP - in production, require email verification

    return user;
  }
}
