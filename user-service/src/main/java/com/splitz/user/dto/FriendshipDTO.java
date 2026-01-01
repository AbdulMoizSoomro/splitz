package com.splitz.user.dto;

import java.time.LocalDateTime;

import com.splitz.user.model.FriendshipStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FriendshipDTO {
    private Long id;

    private Long requesterId;

    private Long addresseeId;

    private FriendshipStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
