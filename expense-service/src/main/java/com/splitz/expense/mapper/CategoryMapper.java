package com.splitz.expense.mapper;

import com.splitz.expense.dto.CategoryDTO;
import com.splitz.expense.model.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
  CategoryDTO toDTO(Category category);

  Category toEntity(CategoryDTO categoryDTO);
}
