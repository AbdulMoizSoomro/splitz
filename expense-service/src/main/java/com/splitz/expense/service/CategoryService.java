package com.splitz.expense.service;

import com.splitz.expense.dto.CategoryDTO;
import com.splitz.expense.mapper.CategoryMapper;
import com.splitz.expense.repository.CategoryRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final CategoryMapper categoryMapper;

  @Transactional(readOnly = true)
  public List<CategoryDTO> getAllCategories() {
    return categoryRepository.findAll().stream()
        .map(categoryMapper::toDTO)
        .collect(Collectors.toList());
  }
}
