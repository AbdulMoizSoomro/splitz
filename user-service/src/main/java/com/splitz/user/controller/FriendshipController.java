package com.splitz.user.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.splitz.user.dto.FriendshipDTO;
import com.splitz.user.dto.UserDTO;
import com.splitz.user.exception.FriendshipException;
import com.splitz.user.service.FriendshipService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/{userId}/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<FriendshipDTO> sendFriendRequest(
            @PathVariable Long userId,
            @RequestParam Long friendId) {
        try {
            return ResponseEntity.ok(friendshipService.sendFriendRequest(userId, friendId));
        } catch (IllegalArgumentException e) {
            throw new FriendshipException(e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<List<UserDTO>> getFriends(@PathVariable Long userId) {
        return ResponseEntity.ok(friendshipService.getAcceptedFriends(userId));
    }

    @GetMapping("/requests")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<List<FriendshipDTO>> getPendingRequests(@PathVariable Long userId) {
        return ResponseEntity.ok(friendshipService.getPendingRequests(userId));
    }

    @PutMapping("/{friendshipId}/accept")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<FriendshipDTO> acceptFriendRequest(
            @PathVariable Long userId,
            @PathVariable Long friendshipId) {
        try {
            return ResponseEntity.ok(friendshipService.acceptFriendRequest(friendshipId, userId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new FriendshipException(e.getMessage());
        }
    }

    @PutMapping("/{friendshipId}/reject")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<FriendshipDTO> rejectFriendRequest(
            @PathVariable Long userId,
            @PathVariable Long friendshipId) {
        try {
            return ResponseEntity.ok(friendshipService.rejectFriendRequest(friendshipId, userId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new FriendshipException(e.getMessage());
        }
    }

    @DeleteMapping("/{friendId}")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<Void> removeFriend(
            @PathVariable Long userId,
            @PathVariable Long friendId) {
        try {
            friendshipService.removeFriend(userId, friendId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new FriendshipException(e.getMessage());
        }
    }
}
