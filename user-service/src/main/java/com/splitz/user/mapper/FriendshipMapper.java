package com.splitz.user.mapper;

import java.util.List;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;

import com.splitz.user.dto.FriendshipDTO;
import com.splitz.user.model.Friendship;

@Mapper(componentModel = "spring")
public interface FriendshipMapper {

    default FriendshipDTO toDTO(Friendship friendship) {
        if (friendship == null) {
            return null;
        }

        FriendshipDTO dto = new FriendshipDTO();
        dto.setId(friendship.getId());
        dto.setRequesterId(friendship.getRequester() != null ? friendship.getRequester().getId() : null);
        dto.setAddresseeId(friendship.getAddressee() != null ? friendship.getAddressee().getId() : null);
        dto.setStatus(friendship.getStatus());
        dto.setCreatedAt(friendship.getCreatedAt());
        dto.setUpdatedAt(friendship.getUpdatedAt());
        return dto;
    }

    default List<FriendshipDTO> toDTOs(List<Friendship> friendships) {
        if (friendships == null) {
            return List.of();
        }
        return friendships.stream().map(this::toDTO).collect(Collectors.toList());
    }
}