package com.splitz.expense.service;

import com.splitz.expense.dto.CategoryDTO;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.mapper.CategoryMapper;
import com.splitz.expense.model.Category;
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

  @Transactional
  public CategoryDTO createCategory(CategoryDTO request) {
    Category category = categoryMapper.toEntity(request);
    category.setDefaultCategory(false); // Only seed data should be default
    return categoryMapper.toDTO(categoryRepository.save(category));
  }

  @Transactional
  public CategoryDTO updateCategory(Long id, CategoryDTO request) {
    Category category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

    if (request.getName() != null) {
      category.setName(request.getName());
    }
    if (request.getIcon() != null) {
      category.setIcon(request.getIcon());
    }
    if (request.getColor() != null) {
      category.setColor(request.getColor());
    }

    return categoryMapper.toDTO(categoryRepository.save(category));
  }

  @Transactional
  public void deleteCategory(Long id) {
    Category category =
        categoryRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));

    if (category.isDefaultCategory()) {
      throw new IllegalArgumentException("Default categories cannot be deleted");
    }

    categoryRepository.delete(category);
  }
}
