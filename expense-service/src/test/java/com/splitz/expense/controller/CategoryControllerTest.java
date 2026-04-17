package com.splitz.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.expense.dto.CategoryDTO;
import com.splitz.expense.service.CategoryService;
import com.splitz.security.JwtRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private CategoryService categoryService;

  @MockBean private JwtRequestFilter jwtRequestFilter;

  @BeforeEach
  void setUp() throws ServletException, IOException {
    doAnswer(
            invocation -> {
              ServletRequest request = invocation.getArgument(0);
              ServletResponse response = invocation.getArgument(1);
              FilterChain chain = invocation.getArgument(2);
              chain.doFilter(request, response);
              return null;
            })
        .when(jwtRequestFilter)
        .doFilter(any(), any(), any());
  }

  @Test
  @WithMockUser
  void getAllCategories_ShouldReturnListOfCategories() throws Exception {
    // Arrange
    CategoryDTO dto1 = CategoryDTO.builder().id(1L).name("Food").build();
    CategoryDTO dto2 = CategoryDTO.builder().id(2L).name("Transport").build();
    List<CategoryDTO> categories = Arrays.asList(dto1, dto2);

    when(categoryService.getAllCategories()).thenReturn(categories);

    // Act & Assert
    mockMvc
        .perform(get("/categories").contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size()").value(2))
        .andExpect(jsonPath("$[0].name").value("Food"))
        .andExpect(jsonPath("$[1].name").value("Transport"));
  }

  @Test
  @WithMockUser
  void createCategory_ShouldReturnCreatedCategory() throws Exception {
    CategoryDTO request =
        CategoryDTO.builder().name("Utilities").icon("💡").color("#96CEB4").build();
    CategoryDTO response =
        CategoryDTO.builder().id(3L).name("Utilities").icon("💡").color("#96CEB4").build();

    when(categoryService.createCategory(any(CategoryDTO.class))).thenReturn(response);

    mockMvc
        .perform(
            post("/categories")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(3))
        .andExpect(jsonPath("$.name").value("Utilities"));
  }

  @Test
  @WithMockUser
  void updateCategory_ShouldReturnUpdatedCategory() throws Exception {
    CategoryDTO request = CategoryDTO.builder().name("Updated Food").build();
    CategoryDTO response = CategoryDTO.builder().id(1L).name("Updated Food").build();

    when(categoryService.updateCategory(any(Long.class), any(CategoryDTO.class)))
        .thenReturn(response);

    mockMvc
        .perform(
            put("/categories/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Food"));
  }

  @Test
  @WithMockUser
  void deleteCategory_ShouldReturnNoContent() throws Exception {
    mockMvc.perform(delete("/categories/1").with(csrf())).andExpect(status().isNoContent());

    verify(categoryService).deleteCategory(1L);
  }
}
