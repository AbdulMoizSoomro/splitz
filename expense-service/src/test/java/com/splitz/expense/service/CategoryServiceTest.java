package com.splitz.expense.service;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.splitz.expense.dto.CategoryDTO;
import com.splitz.expense.mapper.CategoryMapper;
import com.splitz.expense.model.Category;
import com.splitz.expense.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryService categoryService;

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
}
