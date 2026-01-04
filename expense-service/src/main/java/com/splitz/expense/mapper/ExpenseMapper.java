package com.splitz.expense.mapper;

import com.splitz.expense.dto.ExpenseDTO;
import com.splitz.expense.model.Expense;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
    componentModel = "spring",
    uses = {ExpenseSplitMapper.class})
public interface ExpenseMapper {

  @Mapping(source = "group.id", target = "groupId")
  @Mapping(source = "category.id", target = "categoryId")
  ExpenseDTO toDTO(Expense expense);
}
