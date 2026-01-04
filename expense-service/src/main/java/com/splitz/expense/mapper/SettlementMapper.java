package com.splitz.expense.mapper;

import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.model.Settlement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SettlementMapper {

  @Mapping(source = "group.id", target = "groupId")
  SettlementDTO toDTO(Settlement settlement);
}
