package com.splitz.user.mapper;

import org.mapstruct.Mapper;

import com.splitz.user.dto.RoleDTO;
import com.splitz.user.model.Role;

@Mapper(componentModel = "spring")
public interface RoleMapper {
    RoleDTO toDTO(Role role);

    Role toEntity(RoleDTO roleDTO);
}
