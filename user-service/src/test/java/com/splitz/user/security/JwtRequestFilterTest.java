package com.splitz.user.security;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.splitz.security.JwtRequestFilter;
import com.splitz.security.JwtUtil;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.model.User;
import com.splitz.user.service.UserService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;

@SpringBootTest
@org.springframework.test.context.ActiveProfiles("test")
class JwtRequestFilterTest {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private User createUser() {
        UserDTO dto = new UserDTO();
        String suffix = UUID.randomUUID().toString();
        dto.setUsername("u_" + suffix);
        dto.setEmail("u_" + suffix + "@example.com");
        dto.setFirstName("F");
        dto.setLastName("L");
        dto.setPassword("P@ssw0rd");
        userService.createUser(dto);
        return userService.findByusername(dto.getUsername()).orElseThrow();
    }

    @Test
    void filterSetsAuthenticationPrincipalToUserEntity() throws ServletException, IOException {
        User saved = createUser();
        String token = jwtUtil.generateToken(saved);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        jwtRequestFilter.doFilter(req, resp, chain);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(User.class);
        User principal = (User) auth.getPrincipal();
        assertThat(principal.getId()).isEqualTo(saved.getId());
    }
}
