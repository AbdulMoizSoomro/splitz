package com.splitz.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.exception.UserAlreadyExistsException;
import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("UserController Integration Tests")
class UserControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private UserService userService;

        @Autowired
        private ObjectMapper objectMapper;

        /**
         * Factory method to create a valid UserDTO for testing
         */
        private UserDTO createValidUserDTO() {
                return new UserDTO(
                                null, // id (not provided on creation)
                                "johndoe",
                                "john@example.com",
                                "John",
                                "Doe",
                                "password123");
        }

        /**
         * Factory method to create a UserDTO with ID for testing responses
         */
        private UserDTO createValidUserDTO(Long id) {
                return new UserDTO(
                                id,
                                "johndoe",
                                "john@example.com",
                                "John",
                                "Doe",
                                null // password not returned
                );
        }

        // ============ CREATE USER TESTS ============

        @Nested
        @DisplayName("POST /users - Create User")
        class CreateUserTests {

                @Test
                @DisplayName("Should create user and return 201 CREATED with valid UserDTO")
                void testCreateUser_WhenValidDTO_ThenReturnsCreatedStatus() throws Exception {
                        // Arrange
                        UserDTO requestDTO = createValidUserDTO();
                        UserDTO responseDTO = createValidUserDTO(1L);

                        when(userService.createUser(any(UserDTO.class)))
                                        .thenReturn(responseDTO);

                        // Act & Assert
                        mockMvc.perform(post("/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(requestDTO)))
                                        .andExpect(status().isCreated())
                                        .andExpect(jsonPath("$.id", is(1)))
                                        .andExpect(jsonPath("$.username", is("johndoe")))
                                        .andExpect(jsonPath("$.email", is("john@example.com")))
                                        .andExpect(jsonPath("$.firstName", is("John")))
                                        .andExpect(jsonPath("$.lastName", is("Doe")));

                        verify(userService, times(1)).createUser(any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 409 CONFLICT when username already exists")
                void testCreateUser_WhenUsernameExists_ThenReturnsConflict() throws Exception {
                        // Arrange
                        UserDTO requestDTO = createValidUserDTO();

                        when(userService.createUser(any(UserDTO.class)))
                                        .thenThrow(new UserAlreadyExistsException("Username already exists: johndoe"));

                        // Act & Assert
                        mockMvc.perform(post("/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(requestDTO)))
                                        .andExpect(status().isConflict())
                                        .andExpect(jsonPath("$.status", is(409)))
                                        .andExpect(jsonPath("$.title", is("User Already Exists")))
                                        .andExpect(jsonPath("$.detail", containsString("Username already exists")));

                        verify(userService, times(1)).createUser(any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 409 CONFLICT when email already exists")
                void testCreateUser_WhenEmailExists_ThenReturnsConflict() throws Exception {
                        // Arrange
                        UserDTO requestDTO = createValidUserDTO();

                        when(userService.createUser(any(UserDTO.class)))
                                        .thenThrow(new UserAlreadyExistsException(
                                                        "Email already exists: john@example.com"));

                        // Act & Assert
                        mockMvc.perform(post("/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(requestDTO)))
                                        .andExpect(status().isConflict());

                        verify(userService, times(1)).createUser(any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 400 BAD REQUEST when username is missing")
                void testCreateUser_WhenUsernameBlank_ThenReturnsBadRequest() throws Exception {
                        // Arrange
                        UserDTO requestDTO = createValidUserDTO();
                        requestDTO.setUsername(""); // Invalid: blank username

                        // Act & Assert
                        mockMvc.perform(post("/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(requestDTO)))
                                        .andExpect(status().isBadRequest());

                        verify(userService, never()).createUser(any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 400 BAD REQUEST when email is invalid")
                void testCreateUser_WhenEmailInvalid_ThenReturnsBadRequest() throws Exception {
                        // Arrange
                        UserDTO requestDTO = createValidUserDTO();
                        requestDTO.setEmail("invalid-email"); // Invalid email format

                        // Act & Assert
                        mockMvc.perform(post("/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(requestDTO)))
                                        .andExpect(status().isBadRequest());

                        verify(userService, never()).createUser(any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 400 BAD REQUEST when password is missing")
                void testCreateUser_WhenPasswordMissing_ThenReturnsBadRequest() throws Exception {
                        // Arrange
                        UserDTO requestDTO = createValidUserDTO();
                        requestDTO.setPassword(null); // Invalid: null password

                        // Act & Assert
                        mockMvc.perform(post("/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(requestDTO)))
                                        .andExpect(status().isBadRequest());

                        verify(userService, never()).createUser(any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 400 BAD REQUEST when firstName is missing")
                void testCreateUser_WhenFirstNameMissing_ThenReturnsBadRequest() throws Exception {
                        // Arrange
                        UserDTO requestDTO = createValidUserDTO();
                        requestDTO.setFirstName(null); // Invalid: null firstName

                        // Act & Assert
                        mockMvc.perform(post("/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(requestDTO)))
                                        .andExpect(status().isBadRequest());

                        verify(userService, never()).createUser(any(UserDTO.class));
                }
        }

        // ============ GET ALL USERS TESTS ============

        @Nested
        @DisplayName("GET /users - Get All Users")
        class GetAllUsersTests {

                @Test
                @DisplayName("Should return all users with 200 OK")
                void testGetAllUsers_WhenUsersExist_ThenReturnsListOfUsers() throws Exception {
                        // Arrange
                        List<UserDTO> users = new ArrayList<>();
                        users.add(createValidUserDTO(1L));
                        users.add(createValidUserDTO(2L));

                        when(userService.getAllUsers()).thenReturn(users);

                        // Act & Assert
                        mockMvc.perform(get("/users")
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(2)))
                                        .andExpect(jsonPath("$[0].id", is(1)))
                                        .andExpect(jsonPath("$[0].username", is("johndoe")))
                                        .andExpect(jsonPath("$[1].id", is(2)));

                        verify(userService, times(1)).getAllUsers();
                }

                @Test
                @DisplayName("Should return empty list with 200 OK when no users exist")
                void testGetAllUsers_WhenNoUsers_ThenReturnsEmptyList() throws Exception {
                        // Arrange
                        when(userService.getAllUsers()).thenReturn(new ArrayList<>());

                        // Act & Assert
                        mockMvc.perform(get("/users")
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$", hasSize(0)));

                        verify(userService, times(1)).getAllUsers();
                }

                @Test
                @DisplayName("Should return 500 INTERNAL SERVER ERROR when service throws exception")
                void testGetAllUsers_WhenServiceThrowsException_ThenReturnsInternalServerError() throws Exception {
                        // Arrange
                        when(userService.getAllUsers())
                                        .thenThrow(new RuntimeException("Database connection failed"));

                        // Act & Assert
                        mockMvc.perform(get("/users")
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isInternalServerError());

                        verify(userService, times(1)).getAllUsers();
                }
        }

        // ============ GET USER BY ID TESTS ============

        @Nested
        @DisplayName("GET /users/{id} - Get User By ID")
        class GetUserByIdTests {

                @Test
                @DisplayName("Should return user with 200 OK when user exists")
                void testGetUserById_WhenUserExists_ThenReturnsUser() throws Exception {
                        // Arrange
                        Long userId = 1L;
                        UserDTO user = createValidUserDTO(userId);

                        when(userService.getUserbyId(userId)).thenReturn(Optional.of(user));

                        // Act & Assert
                        mockMvc.perform(get("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id", is(1)))
                                        .andExpect(jsonPath("$.username", is("johndoe")))
                                        .andExpect(jsonPath("$.email", is("john@example.com")));

                        verify(userService, times(1)).getUserbyId(userId);
                }

                @Test
                @DisplayName("Should return 404 NOT FOUND when user does not exist")
                void testGetUserById_WhenUserNotFound_ThenReturnsNotFound() throws Exception {
                        // Arrange
                        Long userId = 999L;
                        when(userService.getUserbyId(userId)).thenReturn(Optional.empty());

                        // Act & Assert
                        mockMvc.perform(get("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isNotFound());

                        verify(userService, times(1)).getUserbyId(userId);
                }

                @Test
                @DisplayName("Should return 500 INTERNAL SERVER ERROR when service throws exception")
                void testGetUserById_WhenServiceThrowsException_ThenReturnsInternalServerError() throws Exception {
                        // Arrange
                        Long userId = 1L;
                        when(userService.getUserbyId(userId))
                                        .thenThrow(new RuntimeException("Database error"));

                        // Act & Assert
                        mockMvc.perform(get("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isInternalServerError());

                        verify(userService, times(1)).getUserbyId(userId);
                }
        }

        // ============ UPDATE USER TESTS ============

        @Nested
        @DisplayName("PUT /users/{id} - Update User")
        class UpdateUserTests {

                @Test
                @DisplayName("Should update user and return 200 OK with updated UserDTO")
                void testUpdateUser_WhenValidDTOAndUserExists_ThenReturnsUpdatedUser() throws Exception {
                        // Arrange
                        Long userId = 1L;
                        UserDTO updateDTO = new UserDTO(
                                        userId,
                                        "johndoe",
                                        "john@example.com",
                                        "Jonathan", // Updated firstName
                                        "Doe",
                                        "newpassword123");
                        UserDTO responseDTO = new UserDTO(
                                        userId,
                                        "johndoe",
                                        "john@example.com",
                                        "Jonathan",
                                        "Doe",
                                        null // password not returned
                        );

                        when(userService.updateUser(eq(userId), any(UserDTO.class)))
                                        .thenReturn(responseDTO);

                        // Act & Assert
                        mockMvc.perform(put("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(updateDTO)))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.id", is(1)))
                                        .andExpect(jsonPath("$.firstName", is("Jonathan")))
                                        .andExpect(jsonPath("$.username", is("johndoe")));

                        verify(userService, times(1)).updateUser(eq(userId), any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 404 NOT FOUND when user does not exist")
                void testUpdateUser_WhenUserNotFound_ThenReturnsNotFound() throws Exception {
                        // Arrange
                        Long userId = 999L;
                        UserDTO updateDTO = createValidUserDTO();

                        when(userService.updateUser(eq(userId), any(UserDTO.class)))
                                        .thenThrow(new RuntimeException("User not found"));

                        // Act & Assert
                        mockMvc.perform(put("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(updateDTO)))
                                        .andExpect(status().isNotFound());

                        verify(userService, times(1)).updateUser(eq(userId), any(UserDTO.class));
                }

                @Test
                @DisplayName("Should return 400 BAD REQUEST when firstName is invalid")
                void testUpdateUser_WhenFirstNameInvalid_ThenReturnsBadRequest() throws Exception {
                        // Arrange
                        Long userId = 1L;
                        UserDTO updateDTO = createValidUserDTO();
                        updateDTO.setFirstName(""); // Invalid: blank firstName

                        // Act & Assert
                        mockMvc.perform(put("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(updateDTO)))
                                        .andExpect(status().isBadRequest());

                        verify(userService, never()).updateUser(anyLong(), any(UserDTO.class));
                }
        }

        // ============ DELETE USER TESTS ============

        @Nested
        @DisplayName("DELETE /users/{id} - Delete User")
        class DeleteUserTests {

                @Test
                @DisplayName("Should delete user and return 204 NO CONTENT")
                void testDeleteUser_WhenUserExists_ThenReturnsNoContent() throws Exception {
                        // Arrange
                        Long userId = 1L;
                        doNothing().when(userService).deleteUser(userId);

                        // Act & Assert
                        mockMvc.perform(delete("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isNoContent());

                        verify(userService, times(1)).deleteUser(userId);
                }

                @Test
                @DisplayName("Should return 404 NOT FOUND when user does not exist")
                void testDeleteUser_WhenUserNotFound_ThenReturnsNotFound() throws Exception {
                        // Arrange
                        Long userId = 999L;
                        doThrow(new RuntimeException("User not found"))
                                        .when(userService).deleteUser(userId);

                        // Act & Assert
                        mockMvc.perform(delete("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isNotFound());

                        verify(userService, times(1)).deleteUser(userId);
                }

                @Test
                @DisplayName("Should return 404 NOT FOUND when service throws exception")
                void testDeleteUser_WhenServiceThrowsException_ThenReturnsNotFound() throws Exception {
                        // Arrange
                        Long userId = 1L;
                        doThrow(new RuntimeException("Database error"))
                                        .when(userService).deleteUser(userId);

                        // Act & Assert
                        mockMvc.perform(delete("/users/{id}", userId)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isNotFound());

                        verify(userService, times(1)).deleteUser(userId);
                }
        }

        // ============ SEARCH USERS TESTS ============

        @Nested
        @DisplayName("GET /users/search - Search Users")
        class SearchUsersTests {

                @Test
                @DisplayName("Should return paginated search results with 200 OK")
                void testSearchUsers_WhenUsersMatch_ThenReturnsPageOfUsers() throws Exception {
                        // Arrange
                        String query = "john";
                        Pageable pageable = PageRequest.of(0, 10);
                        List<UserDTO> userList = new ArrayList<>();
                        userList.add(createValidUserDTO(1L));
                        Page<UserDTO> page = new PageImpl<>(userList, pageable, 1);

                        when(userService.searchUsers(eq(query), any(Pageable.class)))
                                        .thenReturn(page);

                        // Act & Assert
                        mockMvc.perform(get("/users/search")
                                        .param("query", query)
                                        .param("page", "0")
                                        .param("size", "10")
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content", hasSize(1)))
                                        .andExpect(jsonPath("$.content[0].username", is("johndoe")))
                                        .andExpect(jsonPath("$.totalElements", is(1)))
                                        .andExpect(jsonPath("$.number", is(0)))
                                        .andExpect(jsonPath("$.size", is(10)));

                        verify(userService, times(1)).searchUsers(eq(query), any(Pageable.class));
                }

                @Test
                @DisplayName("Should return empty page with 200 OK when no matches found")
                void testSearchUsers_WhenNoMatches_ThenReturnsEmptyPage() throws Exception {
                        // Arrange
                        String query = "nonexistent";
                        Pageable pageable = PageRequest.of(0, 10);
                        Page<UserDTO> emptyPage = new PageImpl<>(new ArrayList<>(), pageable, 0);

                        when(userService.searchUsers(eq(query), any(Pageable.class)))
                                        .thenReturn(emptyPage);

                        // Act & Assert
                        mockMvc.perform(get("/users/search")
                                        .param("query", query)
                                        .param("page", "0")
                                        .param("size", "10")
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content", hasSize(0)))
                                        .andExpect(jsonPath("$.totalElements", is(0)));

                        verify(userService, times(1)).searchUsers(eq(query), any(Pageable.class));
                }

                @Test
                @DisplayName("Should use default pagination when page/size parameters are missing")
                void testSearchUsers_WhenPaginationParametersOmitted_ThenUsesDefaults() throws Exception {
                        // Arrange
                        String query = "john";
                        Pageable pageable = PageRequest.of(0, 10);
                        List<UserDTO> userList = new ArrayList<>();
                        userList.add(createValidUserDTO(1L));
                        Page<UserDTO> page = new PageImpl<>(userList, pageable, 1);

                        when(userService.searchUsers(eq(query), any(Pageable.class)))
                                        .thenReturn(page);

                        // Act & Assert
                        mockMvc.perform(get("/users/search")
                                        .param("query", query)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk())
                                        .andExpect(jsonPath("$.content", hasSize(1)));

                        verify(userService, times(1)).searchUsers(eq(query), any(Pageable.class));
                }

                @Test
                @DisplayName("Should return 500 INTERNAL SERVER ERROR when service throws exception")
                void testSearchUsers_WhenServiceThrowsException_ThenReturnsInternalServerError() throws Exception {
                        // Arrange
                        String query = "john";
                        when(userService.searchUsers(eq(query), any(Pageable.class)))
                                        .thenThrow(new RuntimeException("Database error"));

                        // Act & Assert
                        mockMvc.perform(get("/users/search")
                                        .param("query", query)
                                        .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isInternalServerError());

                        verify(userService, times(1)).searchUsers(eq(query), any(Pageable.class));
                }
        }
}
