package com.splitz.expense.mapper;

import com.splitz.expense.dto.ExpenseSplitDTO;
import com.splitz.expense.model.ExpenseSplit;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ExpenseSplitMapper {

  ExpenseSplitDTO toDTO(ExpenseSplit split);
}
