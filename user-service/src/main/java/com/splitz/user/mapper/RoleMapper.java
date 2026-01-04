package com.splitz.user.mapper;

import com.splitz.user.dto.RoleDTO;
import com.splitz.user.model.Role;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface RoleMapper {
  RoleDTO toDTO(Role role);

  Role toEntity(RoleDTO roleDTO);
}
