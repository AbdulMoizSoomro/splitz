package com.splitz.user.integration;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitz.user.model.Role;
import com.splitz.user.model.User;
import com.splitz.user.repository.FriendshipRepository;
import com.splitz.user.repository.RoleRepository;
import com.splitz.user.repository.UserRepository;
import com.splitz.user.security.AuthController.JwtRequest;
import com.splitz.user.security.AuthController.JwtResponse;

/**
 * Integration tests for Friendship endpoints covering the full flow:
 * send request -> view pending -> accept -> list friends -> remove.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Friendship Integration Tests")
@ActiveProfiles("test")
class FriendshipIntegrationTest {

        @LocalServerPort
        private int port;

        @Autowired
        private TestRestTemplate restTemplate;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private RoleRepository roleRepository;

        @Autowired
        private FriendshipRepository friendshipRepository;

        @Autowired
        private BCryptPasswordEncoder passwordEncoder;

        @Autowired
        private ObjectMapper objectMapper;

        private String baseUrl;
        private Long aliceId;
        private Long bobId;

        @BeforeEach
        void setUp() {
                baseUrl = "http://localhost:" + port;

                // Clean up friendships and test users to keep isolation
                friendshipRepository.deleteAll();
                userRepository.findByusername("friend_alice").ifPresent(userRepository::delete);
                userRepository.findByusername("friend_bob").ifPresent(userRepository::delete);

                Role userRole = roleRepository.findByName("ROLE_USER")
                                .orElseGet(() -> roleRepository.save(new Role("ROLE_USER")));

                aliceId = createUser("friend_alice", "alice@example.com", "password123", userRole).getId();
                bobId = createUser("friend_bob", "bob@example.com", "password123", userRole).getId();
        }

        private User createUser(String username, String email, String rawPassword, Role role) {
                Set<Role> roles = new HashSet<>();
                roles.add(role);

                User user = new User("Test", username, passwordEncoder.encode(rawPassword), roles);
                user.setEmail(email);
                user.setLastName("User");
                user.setEnabled(true);
                user.setVerified(true);
                return userRepository.save(user);
        }

        private String authenticateAndGetToken(String username, String password) {
                JwtRequest authRequest = new JwtRequest(username, password);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<JwtRequest> request = new HttpEntity<>(authRequest, headers);

                ResponseEntity<JwtResponse> authResponse = restTemplate.postForEntity(
                                baseUrl + "/authenticate",
                                request,
                                JwtResponse.class);

                assertThat(authResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(authResponse.getBody()).isNotNull();
                assertThat(authResponse.getBody().token()).isNotBlank();

                return authResponse.getBody().token();
        }

        private HttpHeaders authHeaders(String token) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(token);
                return headers;
        }

        @Test
        @Order(1)
        @DisplayName("Should send, accept, list, and remove friendship")
        void friendshipEndToEndFlow() throws Exception {
                String aliceToken = authenticateAndGetToken("friend_alice", "password123");
                String bobToken = authenticateAndGetToken("friend_bob", "password123");

                HttpHeaders aliceHeaders = authHeaders(aliceToken);
                HttpHeaders bobHeaders = authHeaders(bobToken);

                // Send friend request from Alice to Bob
                ResponseEntity<String> sendResponse = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends?friendId=" + bobId,
                                HttpMethod.POST,
                                new HttpEntity<>(aliceHeaders),
                                String.class);

                assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                JsonNode sendJson = objectMapper.readTree(sendResponse.getBody());
                assertThat(sendJson.get("status").asText()).isEqualTo("PENDING");
                assertThat(sendJson.get("requesterId").asLong()).isEqualTo(aliceId);
                assertThat(sendJson.get("addresseeId").asLong()).isEqualTo(bobId);
                long friendshipId = sendJson.get("id").asLong();

                // Bob checks pending requests
                ResponseEntity<String> pendingResponse = restTemplate.exchange(
                                baseUrl + "/users/" + bobId + "/friends/requests",
                                HttpMethod.GET,
                                new HttpEntity<>(bobHeaders),
                                String.class);
                assertThat(pendingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                JsonNode pendingJson = objectMapper.readTree(pendingResponse.getBody());
                assertThat(pendingJson.isArray()).isTrue();
                assertThat(pendingJson.get(0).get("id").asLong()).isEqualTo(friendshipId);

                // Bob accepts the request
                ResponseEntity<String> acceptResponse = restTemplate.exchange(
                                baseUrl + "/users/" + bobId + "/friends/" + friendshipId + "/accept",
                                HttpMethod.PUT,
                                new HttpEntity<>(bobHeaders),
                                String.class);
                assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                JsonNode acceptJson = objectMapper.readTree(acceptResponse.getBody());
                assertThat(acceptJson.get("status").asText()).isEqualTo("ACCEPTED");

                // Alice lists accepted friends
                ResponseEntity<String> friendsResponse = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends",
                                HttpMethod.GET,
                                new HttpEntity<>(aliceHeaders),
                                String.class);
                assertThat(friendsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                JsonNode friendsJson = objectMapper.readTree(friendsResponse.getBody());
                assertThat(friendsJson.isArray()).isTrue();
                assertThat(friendsJson.get(0).get("id").asLong()).isEqualTo(bobId);

                // Alice removes the friendship
                ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends/" + bobId,
                                HttpMethod.DELETE,
                                new HttpEntity<>(aliceHeaders),
                                Void.class);
                assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

                // Friends list should now be empty
                ResponseEntity<String> afterDeleteResponse = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends",
                                HttpMethod.GET,
                                new HttpEntity<>(aliceHeaders),
                                String.class);
                assertThat(afterDeleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                JsonNode afterDeleteJson = objectMapper.readTree(afterDeleteResponse.getBody());
                assertThat(afterDeleteJson.isArray()).isTrue();
                assertThat(afterDeleteJson.size()).isZero();
        }

        @Test
        @Order(2)
        @DisplayName("Should reject sending a friend request to yourself")
        void selfFriendRequestShouldFail() throws Exception {
                String aliceToken = authenticateAndGetToken("friend_alice", "password123");
                HttpHeaders aliceHeaders = authHeaders(aliceToken);

                ResponseEntity<String> response = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends?friendId=" + aliceId,
                                HttpMethod.POST,
                                new HttpEntity<>(aliceHeaders),
                                String.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                JsonNode problem = objectMapper.readTree(response.getBody());
                assertThat(problem.get("detail").asText()).contains("yourself");
        }

        @Test
        @Order(3)
        @DisplayName("Should reject duplicate friend requests between the same users")
        void duplicateFriendRequestShouldFail() throws Exception {
                String aliceToken = authenticateAndGetToken("friend_alice", "password123");
                HttpHeaders aliceHeaders = authHeaders(aliceToken);

                ResponseEntity<String> first = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends?friendId=" + bobId,
                                HttpMethod.POST,
                                new HttpEntity<>(aliceHeaders),
                                String.class);
                assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

                ResponseEntity<String> second = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends?friendId=" + bobId,
                                HttpMethod.POST,
                                new HttpEntity<>(aliceHeaders),
                                String.class);

                assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                JsonNode problem = objectMapper.readTree(second.getBody());
                assertThat(problem.get("detail").asText()).contains("already exists");
        }

        @Test
        @Order(4)
        @DisplayName("Should block requester from accepting their own outgoing request")
        void requesterCannotAcceptOwnRequest() throws Exception {
                String aliceToken = authenticateAndGetToken("friend_alice", "password123");
                String bobToken = authenticateAndGetToken("friend_bob", "password123");

                HttpHeaders aliceHeaders = authHeaders(aliceToken);
                HttpHeaders bobHeaders = authHeaders(bobToken);

                ResponseEntity<String> sendResponse = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends?friendId=" + bobId,
                                HttpMethod.POST,
                                new HttpEntity<>(aliceHeaders),
                                String.class);
                assertThat(sendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                long friendshipId = objectMapper.readTree(sendResponse.getBody()).get("id").asLong();

                ResponseEntity<String> acceptResponse = restTemplate.exchange(
                                baseUrl + "/users/" + aliceId + "/friends/" + friendshipId + "/accept",
                                HttpMethod.PUT,
                                new HttpEntity<>(aliceHeaders),
                                String.class);

                assertThat(acceptResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                JsonNode problem = objectMapper.readTree(acceptResponse.getBody());
                assertThat(problem.get("detail").asText()).contains("Only the addressee");

                // Ensure Bob (addressee) can still accept successfully
                ResponseEntity<String> bobAccept = restTemplate.exchange(
                                baseUrl + "/users/" + bobId + "/friends/" + friendshipId + "/accept",
                                HttpMethod.PUT,
                                new HttpEntity<>(bobHeaders),
                                String.class);
                assertThat(bobAccept.getStatusCode()).isEqualTo(HttpStatus.OK);
                JsonNode acceptedJson = objectMapper.readTree(bobAccept.getBody());
                assertThat(acceptedJson.get("status").asText()).isEqualTo("ACCEPTED");
        }
}
