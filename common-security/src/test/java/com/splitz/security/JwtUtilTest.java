package com.splitz.security;

import java.security.Key;
import java.util.ArrayList;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"; // 256-bit key
    private long expiration = 1000 * 60 * 60; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "expiration", expiration);
        jwtUtil.init();
    }

    @Test
    void generateToken_ShouldReturnToken() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractUsername_ShouldReturnCorrectUsername() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        String username = jwtUtil.extractUsername(token);
        assertEquals("testuser", username);
    }

    @Test
    void isTokenValid_ShouldReturnTrueForValidToken() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        assertTrue(jwtUtil.isTokenValid(token, userDetails));
    }

    @Test
    void isTokenValid_ShouldReturnFalseForInvalidUsername() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        UserDetails otherUser = new User("otheruser", "password", new ArrayList<>());
        assertFalse(jwtUtil.isTokenValid(token, otherUser));
    }

    @Test
    void isTokenExpired_ShouldReturnFalseForNewToken() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        assertFalse(jwtUtil.isTokenExpired(token));
    }
    
    @Test
    void isTokenExpired_ShouldThrowExceptionForExpiredToken() {
        // Manually create an expired token
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        Key key = Keys.hmacShaKeyFor(keyBytes);
        
        String token = Jwts.builder()
                .subject("testuser")
                .issuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 2)) // 2 hours ago
                .expiration(new Date(System.currentTimeMillis() - 1000 * 60 * 60)) // 1 hour ago
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
                
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> {
            jwtUtil.isTokenExpired(token);
        });
    }
}
