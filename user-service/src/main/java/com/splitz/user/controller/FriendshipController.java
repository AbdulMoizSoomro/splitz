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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/{userId}/friends")
@RequiredArgsConstructor
@Tag(name = "Friendships", description = "Endpoints for managing user connections and friend requests")
public class FriendshipController {

    private final FriendshipService friendshipService;

    @Operation(summary = "Send friend request", description = "Sends a friend request from one user to another. Only the user themselves or an ADMIN can perform this action.", responses = {
            @ApiResponse(responseCode = "200", description = "Friend request sent successfully", content = @Content(schema = @Schema(implementation = FriendshipDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request (e.g., friending self, duplicate request)", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not owner or admin", content = @Content)
    })
    @PostMapping
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<FriendshipDTO> sendFriendRequest(
            @Parameter(description = "ID of the user sending the request") @PathVariable Long userId,
            @Parameter(description = "ID of the user to friend") @RequestParam Long friendId) {
        try {
            return ResponseEntity.ok(friendshipService.sendFriendRequest(userId, friendId));
        } catch (IllegalArgumentException e) {
            throw new FriendshipException(e.getMessage());
        }
    }

    @Operation(summary = "List friends", description = "Returns a list of all accepted friends for a user. Only the user themselves or an ADMIN can perform this action.", responses = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved friends list"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not owner or admin", content = @Content)
    })
    @GetMapping
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<List<UserDTO>> getFriends(
            @Parameter(description = "ID of the user whose friends to list") @PathVariable Long userId) {
        return ResponseEntity.ok(friendshipService.getAcceptedFriends(userId));
    }

    @Operation(summary = "List pending requests", description = "Returns a list of all pending incoming friend requests for a user. Only the user themselves or an ADMIN can perform this action.", responses = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved pending requests"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not owner or admin", content = @Content)
    })
    @GetMapping("/requests")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<List<FriendshipDTO>> getPendingRequests(
            @Parameter(description = "ID of the user whose pending requests to list") @PathVariable Long userId) {
        return ResponseEntity.ok(friendshipService.getPendingRequests(userId));
    }

    @Operation(summary = "Accept friend request", description = "Accepts a pending friend request. Only the addressee of the request or an ADMIN can perform this action.", responses = {
            @ApiResponse(responseCode = "200", description = "Friend request accepted successfully", content = @Content(schema = @Schema(implementation = FriendshipDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not owner or admin", content = @Content)
    })
    @PutMapping("/{friendshipId}/accept")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<FriendshipDTO> acceptFriendRequest(
            @Parameter(description = "ID of the user accepting the request") @PathVariable Long userId,
            @Parameter(description = "ID of the friendship request to accept") @PathVariable Long friendshipId) {
        try {
            return ResponseEntity.ok(friendshipService.acceptFriendRequest(friendshipId, userId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new FriendshipException(e.getMessage());
        }
    }

    @Operation(summary = "Reject friend request", description = "Rejects a pending friend request. Only the addressee of the request or an ADMIN can perform this action.", responses = {
            @ApiResponse(responseCode = "200", description = "Friend request rejected successfully", content = @Content(schema = @Schema(implementation = FriendshipDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not owner or admin", content = @Content)
    })
    @PutMapping("/{friendshipId}/reject")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<FriendshipDTO> rejectFriendRequest(
            @Parameter(description = "ID of the user rejecting the request") @PathVariable Long userId,
            @Parameter(description = "ID of the friendship request to reject") @PathVariable Long friendshipId) {
        try {
            return ResponseEntity.ok(friendshipService.rejectFriendRequest(friendshipId, userId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new FriendshipException(e.getMessage());
        }
    }

    @Operation(summary = "Remove friend", description = "Removes an existing friendship. Only the user themselves or an ADMIN can perform this action.", responses = {
            @ApiResponse(responseCode = "204", description = "Friend removed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not owner or admin", content = @Content)
    })
    @DeleteMapping("/{friendId}")
    @PreAuthorize("@security.isOwnerOrAdmin(#userId)")
    public ResponseEntity<Void> removeFriend(
            @Parameter(description = "ID of the user performing the removal") @PathVariable Long userId,
            @Parameter(description = "ID of the friend to remove") @PathVariable Long friendId) {
        try {
            friendshipService.removeFriend(userId, friendId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new FriendshipException(e.getMessage());
        }
    }
}
