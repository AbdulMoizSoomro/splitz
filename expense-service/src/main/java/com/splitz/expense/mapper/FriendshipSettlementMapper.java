package com.splitz.expense.mapper;

import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.model.FriendshipSettlement;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FriendshipSettlementMapper {

  FriendshipSettlementDTO toDTO(FriendshipSettlement settlement);
}
