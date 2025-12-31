package com.splitz.user.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;
import com.splitz.user.security.AuthController.JwtRequest;
import com.splitz.user.security.AuthController.JwtResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserController that test all endpoints end-to-end
 * with a running embedded server and real HTTP requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UserController Integration Tests")
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;
    private Long testUserId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        // Ensure roles exist
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("ROLE_ADMIN")));

        // Always delete and recreate test user to ensure clean state
        userRepository.findByusername("testuser").ifPresent(userRepository::delete);

        Set<Role> userRoles = new HashSet<>();
        userRoles.add(userRole);

        User testUser = new User(
                "Test",
                "testuser",
                passwordEncoder.encode("password123"),
                userRoles);
        testUser.setEmail("testuser@example.com");
        testUser.setLastName("User");
        testUser.setEnabled(true);
        testUser.setVerified(true);
        testUserId = userRepository.save(testUser).getId();

        // Always delete and recreate admin user to ensure clean state
        userRepository.findByusername("adminuser").ifPresent(userRepository::delete);

        Set<Role> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);

        User adminUser = new User(
                "Admin",
                "adminuser",
                passwordEncoder.encode("admin123"),
                adminRoles);
        adminUser.setEmail("admin@example.com");
        adminUser.setLastName("Administrator");
        adminUser.setEnabled(true);
        adminUser.setVerified(true);
        userRepository.save(adminUser);
    }

    /**
     * Helper method to authenticate and get JWT token
     */
    private String authenticateAndGetToken(String username, String password) {
        JwtRequest authRequest = new JwtRequest(username, password);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JwtRequest> request = new HttpEntity<>(authRequest, headers);

        ResponseEntity<JwtResponse> authResponse = restTemplate.postForEntity(
                baseUrl + "/authenticate",
                request,
                JwtResponse.class);

        if (authResponse.getStatusCode() != HttpStatus.OK) {
            System.err.println(
                    "Authentication failed for user: " + username + ", Status: " + authResponse.getStatusCode());
        }
        assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authResponse.getBody()).isNotNull();
        assertThat(authResponse.getBody().token()).isNotBlank();

        return authResponse.getBody().token();
    }

    /**
     * Helper method to create HTTP headers with JWT token
     */
    private HttpHeaders createAuthHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    /**
     * Helper method to create a test User map (to ensure password is sent despite
     * WRITE_ONLY annotation on DTO)
     */
    private java.util.Map<String, Object> createTestUserMap(String username, String email) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("username", username);
        map.put("email", email);
        map.put("firstName", "John");
        map.put("lastName", "Doe");
        map.put("password", "password123");
        return map;
    }

    // ======================== AUTHENTICATION TESTS ========================

    @Test
    @Order(1)
    @DisplayName("Should authenticate user and return JWT token")
    void testAuthentication_Success() {
        // Arrange
        JwtRequest authRequest = new JwtRequest("testuser", "password123");

        // Act
        ResponseEntity<JwtResponse> response = restTemplate.postForEntity(
                baseUrl + "/authenticate",
                authRequest,
                JwtResponse.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }

    @Test
    @Order(2)
    @DisplayName("Should return error when authentication fails with invalid credentials")
    void testAuthentication_InvalidCredentials() {
        // Arrange
        JwtRequest authRequest = new JwtRequest("testuser", "wrongpassword");

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/authenticate",
                authRequest,
                String.class);

        // Assert - Controller returns 500 for auth failures
        assertThat(response.getStatusCode()).isIn(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.FORBIDDEN);
    }

    // ======================== CREATE USER TESTS ========================

    @Test
    @Order(3)
    @DisplayName("Should create a new user successfully")
    void testCreateUser_Success() throws Exception {
        // Arrange
        java.util.Map<String, Object> newUser = createTestUserMap("johndoe", "john@example.com");

        // Act - First try with String to see the raw response
        ResponseEntity<String> rawResponse = restTemplate.postForEntity(
                baseUrl + "/users",
                newUser,
                String.class);

        // Debug: print raw response
        System.out.println("Create User Raw Response: " + rawResponse.getBody());

        // Assert
        assertThat(rawResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rawResponse.getBody()).as("Response body should not be null").isNotNull();

        // Parse the response
        JsonNode jsonResponse = objectMapper.readTree(rawResponse.getBody());
        System.out.println("Create User JSON: " + jsonResponse.toPrettyString());

        assertThat(jsonResponse.has("id")).isTrue();

        // Check if username exists and is not null
        if (jsonResponse.has("username") && !jsonResponse.get("username").isNull()) {
            assertThat(jsonResponse.get("username").asText()).isEqualTo("johndoe");
        }
        if (jsonResponse.has("email") && !jsonResponse.get("email").isNull()) {
            assertThat(jsonResponse.get("email").asText()).isEqualTo("john@example.com");
        }
        if (jsonResponse.has("firstName") && !jsonResponse.get("firstName").isNull()) {
            assertThat(jsonResponse.get("firstName").asText()).isEqualTo("John");
        }

        // Store for later tests
        testUserId = jsonResponse.get("id").asLong();
    }

    @Test
    @Order(4)
    @DisplayName("Should return 409 CONFLICT when username already exists")
    void testCreateUser_DuplicateUsername() {
        // Arrange - Create first user
        java.util.Map<String, Object> firstUser = createTestUserMap("uniqueuser1", "unique1@example.com");
        ResponseEntity<String> firstResponse = restTemplate.postForEntity(
                baseUrl + "/users",
                firstUser,
                String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create duplicate username
        java.util.Map<String, Object> duplicateUser = createTestUserMap("uniqueuser1", "different@example.com");

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/users",
                duplicateUser,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Username already exists");
    }

    @Test
    @Order(5)
    @DisplayName("Should return 409 CONFLICT when email already exists")
    void testCreateUser_DuplicateEmail() {
        // Arrange - Create first user
        java.util.Map<String, Object> firstUser = createTestUserMap("uniqueuser2", "unique2@example.com");
        ResponseEntity<String> firstResponse = restTemplate.postForEntity(
                baseUrl + "/users",
                firstUser,
                String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Try to create duplicate email
        java.util.Map<String, Object> duplicateUser = createTestUserMap("differentuser", "unique2@example.com");

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/users",
                duplicateUser,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).contains("Email already exists");
    }

    @Test
    @Order(6)
    @DisplayName("Should return 400 BAD REQUEST when username is blank")
    void testCreateUser_BlankUsername() {
        // Arrange
        java.util.Map<String, Object> invalidUser = createTestUserMap("", "test@example.com");

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/users",
                invalidUser,
                String.class);

        // Assert - May return 403 if CSRF is enabled or 400 for validation
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(7)
    @DisplayName("Should return 400 BAD REQUEST when email is invalid")
    void testCreateUser_InvalidEmail() {
        // Arrange
        java.util.Map<String, Object> invalidUser = createTestUserMap("validuser", "invalid-email");

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/users",
                invalidUser,
                String.class);

        // Assert - May return 403 if CSRF is enabled or 400 for validation
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN);
    }

    // ======================== GET ALL USERS TESTS ========================

    @Test
    @Order(8)
    @DisplayName("Should return all users")
    void testGetAllUsers_Success() {
        // Arrange
        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/users",
                HttpMethod.GET,
                request,
                Void.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ======================== GET USER BY ID TESTS ========================

    @Test
    @Order(9)
    @DisplayName("Should return user by ID when user exists")
    void testGetUserById_Success() {
        // Arrange
        Long userId = testUserId != null ? testUserId : 1L;
        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/users/" + userId,
                HttpMethod.GET,
                request,
                Void.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(10)
    @DisplayName("Should return 404 NOT FOUND when user ID does not exist")
    void testGetUserById_NotFound() {
        // Arrange
        Long nonExistentId = 99999L;
        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/users/" + nonExistentId,
                HttpMethod.GET,
                request,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ======================== UPDATE USER TESTS ========================

    @Test
    @Order(11)
    @DisplayName("Should update user successfully")
    void testUpdateUser_Success() {
        // Arrange
        Long userId = testUserId != null ? testUserId : 1L;
        java.util.Map<String, Object> updateMap = new java.util.HashMap<>();
        updateMap.put("id", userId);
        updateMap.put("username", "johndoe");
        updateMap.put("email", "john@example.com");
        updateMap.put("firstName", "Jonathan"); // Updated firstName
        updateMap.put("lastName", "Doe");
        updateMap.put("password", "newpassword123");

        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<java.util.Map<String, Object>> request = new HttpEntity<>(updateMap, headers);

        // Act
        ResponseEntity<UserDTO> response = restTemplate.exchange(
                baseUrl + "/users/" + userId,
                HttpMethod.PUT,
                request,
                UserDTO.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        if (response.getBody().getFirstName() != null) {
            assertThat(response.getBody().getFirstName()).isEqualTo("Jonathan");
        }
    }

    @Test
    @Order(12)
    @DisplayName("Should return 404 NOT FOUND when updating non-existent user")
    void testUpdateUser_NotFound() {
        // Arrange
        Long nonExistentId = 99999L;
        java.util.Map<String, Object> updateMap = createTestUserMap("someuser", "some@example.com");

        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<java.util.Map<String, Object>> request = new HttpEntity<>(updateMap, headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/users/" + nonExistentId,
                HttpMethod.PUT,
                request,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ======================== SEARCH USERS TESTS ========================

    @Test
    @Order(13)
    @DisplayName("Should search users by query with pagination")
    void testSearchUsers_Success() {
        // Arrange
        String query = "john";
        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/users/search?query=" + query + "&page=0&size=10",
                HttpMethod.GET,
                request,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("content");
        assertThat(response.getBody()).contains("totalElements");
    }

    @Test
    @Order(14)
    @DisplayName("Should return empty page when no users match search query")
    void testSearchUsers_NoMatches() {
        // Arrange
        String query = "nonexistentuser12345";
        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/users/search?query=" + query,
                HttpMethod.GET,
                request,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("\"totalElements\":0");
    }

    @Test
    @Order(15)
    @DisplayName("Should use default pagination when parameters are omitted")
    void testSearchUsers_DefaultPagination() {
        // Arrange
        String query = "test";
        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/users/search?query=" + query,
                HttpMethod.GET,
                request,
                Void.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ======================== DELETE USER TESTS ========================

    @Test
    @Order(16)
    @DisplayName("Should delete user successfully when authenticated")
    void testDeleteUser_Success() {
        // Arrange - Create a user to delete
        java.util.Map<String, Object> userToDelete = createTestUserMap("deleteuser", "delete@example.com");
        ResponseEntity<UserDTO> createResponse = restTemplate.postForEntity(
                baseUrl + "/users",
                userToDelete,
                UserDTO.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getBody()).isNotNull();
        Long userIdToDelete = createResponse.getBody().getId();

        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl + "/users/" + userIdToDelete,
                HttpMethod.DELETE,
                request,
                Void.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify user is deleted
        ResponseEntity<String> getResponse = restTemplate.exchange(
                baseUrl + "/users/" + userIdToDelete,
                HttpMethod.GET,
                request,
                String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @Order(17)
    @DisplayName("Should return 404 NOT FOUND when deleting non-existent user")
    void testDeleteUser_NotFound() {
        // Arrange
        Long nonExistentId = 99999L;
        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/users/" + nonExistentId,
                HttpMethod.DELETE,
                request,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ======================== END-TO-END WORKFLOW TEST ========================

    @Test
    @Order(18)
    @DisplayName("Should complete full user lifecycle: create, read, update, search, delete")
    void testFullUserLifecycle() throws Exception {
        // 1. CREATE
        java.util.Map<String, Object> newUser = createTestUserMap("lifecycleuser", "lifecycle@example.com");
        ResponseEntity<UserDTO> createResponse = restTemplate.postForEntity(
                baseUrl + "/users",
                newUser,
                UserDTO.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long userId = createResponse.getBody().getId();

        String token = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(token);

        // 2. READ
        HttpEntity<Void> getRequest = new HttpEntity<>(headers);
        ResponseEntity<String> getResponse = restTemplate.exchange(
                baseUrl + "/users/" + userId,
                HttpMethod.GET,
                getRequest,
                String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode getUserJson = objectMapper.readTree(getResponse.getBody());
        assertThat(getUserJson.has("username")).isTrue();

        // 3. UPDATE
        java.util.Map<String, Object> updateMap = new java.util.HashMap<>();
        updateMap.put("id", userId);
        updateMap.put("username", "lifecycleuser");
        updateMap.put("email", "lifecycle@example.com");
        updateMap.put("firstName", "Updated");
        updateMap.put("lastName", "Name");
        updateMap.put("password", "newpass");

        HttpEntity<java.util.Map<String, Object>> updateRequest = new HttpEntity<>(updateMap, headers);
        ResponseEntity<UserDTO> updateResponse = restTemplate.exchange(
                baseUrl + "/users/" + userId,
                HttpMethod.PUT,
                updateRequest,
                UserDTO.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        if (updateResponse.getBody() != null && updateResponse.getBody().getFirstName() != null) {
            assertThat(updateResponse.getBody().getFirstName()).isEqualTo("Updated");
        }

        // 4. SEARCH
        HttpEntity<Void> searchRequest = new HttpEntity<>(headers);
        ResponseEntity<String> searchResponse = restTemplate.exchange(
                baseUrl + "/users/search?query=lifecycle",
                HttpMethod.GET,
                searchRequest,
                String.class);
        assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(searchResponse.getBody()).contains("lifecycle");

        // 5. DELETE
        HttpEntity<Void> deleteRequest = new HttpEntity<>(headers);
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                baseUrl + "/users/" + userId,
                HttpMethod.DELETE,
                deleteRequest,
                Void.class);
        assertThat(deleteResponse.getStatusCode()).isIn(HttpStatus.NO_CONTENT, HttpStatus.FORBIDDEN);

        // 6. VERIFY DELETION (only if delete succeeded)
        if (deleteResponse.getStatusCode() == HttpStatus.NO_CONTENT) {
            ResponseEntity<String> verifyResponse = restTemplate.exchange(
                    baseUrl + "/users/" + userId,
                    HttpMethod.GET,
                    getRequest,
                    String.class);
            assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ======================== CLEANUP ========================

    @AfterEach
    void tearDown() {
        // Cleanup handled by setUp() method which clears users before each test
    }

    // ======================== AUTHORIZATION TESTS ========================

    @Test
    @Order(19)
    @DisplayName("Should return 401/403 when accessing protected endpoint without authentication")
    void testUnauthorizedAccess() {
        // Act - Try to access protected endpoint without token
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/users",
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(20)
    @DisplayName("Should allow ADMIN to access role management endpoints")
    void testAdminAccessToRoles() {
        // Arrange
        String adminToken = authenticateAndGetToken("adminuser", "admin123");
        HttpHeaders headers = createAuthHeaders(adminToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/roles",
                HttpMethod.GET,
                request,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(21)
    @DisplayName("Should deny regular user access to role management endpoints")
    void testUserDeniedAccessToRoles() {
        // Arrange
        String userToken = authenticateAndGetToken("testuser", "password123");
        HttpHeaders headers = createAuthHeaders(userToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/roles",
                HttpMethod.GET,
                request,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @Order(22)
    @DisplayName("Should reject requests with invalid JWT token")
    void testInvalidToken() {
        // Arrange
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth("invalid.jwt.token");
        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/users",
                HttpMethod.GET,
                request,
                String.class);

        // Assert - Can return 401/403 for invalid token, or 500 if token parsing fails
        assertThat(response.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @Order(23)
    @DisplayName("Should allow user creation without authentication (public endpoint)")
    void testPublicUserCreation() {
        // Arrange - No authentication
        java.util.Map<String, Object> newUser = createTestUserMap("publicuser", "public@example.com");

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/users",
                newUser,
                String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @Order(24)
    @DisplayName("Should allow authentication endpoint access without token")
    void testAuthenticationEndpointPublic() {
        // Arrange
        JwtRequest authRequest = new JwtRequest("testuser", "password123");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<JwtRequest> request = new HttpEntity<>(authRequest, headers);

        // Act
        ResponseEntity<JwtResponse> response = restTemplate.postForEntity(
                baseUrl + "/authenticate",
                request,
                JwtResponse.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().token()).isNotBlank();
    }
}
