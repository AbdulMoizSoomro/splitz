package com.splitz.user.mapper;


import com.splitz.user.dto.UserDTO;
import com.splitz.user.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserDTO toDTO(User user);
    User toEntity(UserDTO userDTO);
}