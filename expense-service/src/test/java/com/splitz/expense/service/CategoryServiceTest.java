package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.splitz.expense.dto.CategoryDTO;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.mapper.CategoryMapper;
import com.splitz.expense.model.Category;
import com.splitz.expense.repository.CategoryRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

  @Mock private CategoryRepository categoryRepository;

  @Mock private CategoryMapper categoryMapper;

  @InjectMocks private CategoryService categoryService;

  @Test
  void getAllCategories_ShouldReturnListOfCategories() {
    // Arrange
    Category category1 = Category.builder().id(1L).name("Food").build();
    Category category2 = Category.builder().id(2L).name("Transport").build();
    List<Category> categories = Arrays.asList(category1, category2);

    CategoryDTO dto1 = CategoryDTO.builder().id(1L).name("Food").build();
    CategoryDTO dto2 = CategoryDTO.builder().id(2L).name("Transport").build();

    when(categoryRepository.findAll()).thenReturn(categories);
    when(categoryMapper.toDTO(category1)).thenReturn(dto1);
    when(categoryMapper.toDTO(category2)).thenReturn(dto2);

    // Act
    List<CategoryDTO> result = categoryService.getAllCategories();

    // Assert
    assertEquals(2, result.size());
    assertEquals("Food", result.get(0).getName());
    assertEquals("Transport", result.get(1).getName());
  }

  @Test
  void createCategory_ShouldReturnCreatedCategory() {
    CategoryDTO request =
        CategoryDTO.builder().name("Utilities").icon("💡").color("#96CEB4").build();
    Category category =
        Category.builder().id(3L).name("Utilities").icon("💡").color("#96CEB4").build();
    CategoryDTO response =
        CategoryDTO.builder().id(3L).name("Utilities").icon("💡").color("#96CEB4").build();

    when(categoryMapper.toEntity(request)).thenReturn(category);
    when(categoryRepository.save(any(Category.class))).thenReturn(category);
    when(categoryMapper.toDTO(category)).thenReturn(response);

    CategoryDTO result = categoryService.createCategory(request);

    assertNotNull(result);
    assertEquals("Utilities", result.getName());
    verify(categoryRepository).save(any(Category.class));
  }

  @Test
  void updateCategory_ShouldReturnUpdatedCategory() {
    CategoryDTO request =
        CategoryDTO.builder().name("Updated Food").icon("🍔").color("#FF0000").build();
    Category existingCategory = Category.builder().id(1L).name("Food").build();
    CategoryDTO response =
        CategoryDTO.builder().id(1L).name("Updated Food").icon("🍔").color("#FF0000").build();

    when(categoryRepository.findById(1L)).thenReturn(Optional.of(existingCategory));
    when(categoryRepository.save(any(Category.class))).thenReturn(existingCategory);
    when(categoryMapper.toDTO(existingCategory)).thenReturn(response);

    CategoryDTO result = categoryService.updateCategory(1L, request);

    assertNotNull(result);
    assertEquals("Updated Food", result.getName());
    verify(categoryRepository).save(any(Category.class));
  }

  @Test
  void deleteCategory_ShouldDeleteCategory() {
    Category category = Category.builder().id(1L).name("Food").defaultCategory(false).build();
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

    categoryService.deleteCategory(1L);

    verify(categoryRepository).delete(category);
  }

  @Test
  void deleteCategory_DefaultCategory_ShouldThrowException() {
    Category category = Category.builder().id(1L).name("Food").defaultCategory(true).build();
    when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

    assertThrows(IllegalArgumentException.class, () -> categoryService.deleteCategory(1L));
  }

  @Test
  void updateCategory_NotFound_ShouldThrowException() {
    when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class,
        () -> categoryService.updateCategory(1L, CategoryDTO.builder().build()));
  }
}
