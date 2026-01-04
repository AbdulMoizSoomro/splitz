package com.splitz.expense.mapper;

import com.splitz.expense.dto.GroupMemberDTO;
import com.splitz.expense.model.GroupMember;
import org.springframework.stereotype.Component;

/**
 * Lightweight manual mapper to avoid MapStruct runtime classpath issues in
 * tests.
 */
@Component
public class GroupMemberMapper {

    public GroupMemberDTO toDTO(GroupMember member) {
        if (member == null) {
            return null;
        }

        return GroupMemberDTO.builder()
                .userId(member.getUserId())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
