package com.splitz.expense.service;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.splitz.expense.dto.AddMemberRequest;
import com.splitz.expense.dto.CreateGroupRequest;
import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.UpdateGroupRequest;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.mapper.GroupMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupRole;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupMapper groupMapper;

    @InjectMocks
    private GroupService groupService;

    @Test
    void createGroup_ShouldAddCreatorAsAdmin() {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Roommates");
        request.setDescription("desc");

        Group saved = Group.builder()
                .id(1L)
                .name("Roommates")
                .active(true)
                .members(Set.of(GroupMember.builder().userId(99L).role(GroupRole.ADMIN).build()))
                .build();

        GroupDTO dto = GroupDTO.builder().id(1L).name("Roommates").build();

        when(groupRepository.save(any(Group.class))).thenReturn(saved);
        when(groupMapper.toDTO(saved)).thenReturn(dto);

        GroupDTO result = groupService.createGroup(request, 99L);

        verify(groupRepository, times(1)).save(any(Group.class));
        verify(groupMapper).toDTO(saved);
        assertEquals("Roommates", result.getName());
    }

    @Test
    void updateGroup_WhenUserIsNotAdmin_ShouldThrow() {
        Group group = Group.builder()
                .id(2L)
                .name("Trip")
                .members(Set.of(GroupMember.builder().userId(5L).role(GroupRole.MEMBER).build()))
                .build();

        when(groupRepository.findById(2L)).thenReturn(Optional.of(group));

        UpdateGroupRequest updateRequest = new UpdateGroupRequest();
        updateRequest.setName("New Name");

        assertThrows(AccessDeniedException.class, () -> groupService.updateGroup(2L, updateRequest, 5L));
    }

    @Test
    void addMember_WhenAlreadyExists_ShouldThrow() {
        Group group = Group.builder()
                .id(3L)
                .members(Set.of(GroupMember.builder().userId(1L).role(GroupRole.ADMIN).build()))
                .build();

        when(groupRepository.findById(3L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.existsByGroupIdAndUserId(3L, 1L)).thenReturn(true);

        AddMemberRequest addMemberRequest = new AddMemberRequest();
        addMemberRequest.setUserId(1L);

        assertThrows(IllegalArgumentException.class, () -> groupService.addMember(3L, addMemberRequest, 1L));
    }

    @Test
    void removeMember_WhenNotFound_ShouldThrowResourceNotFound() {
        Group group = Group.builder()
                .id(4L)
                .members(Set.of(GroupMember.builder().userId(10L).role(GroupRole.ADMIN).build()))
                .build();

        when(groupRepository.findById(4L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByGroupIdAndUserId(4L, 20L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> groupService.removeMember(4L, 20L, 10L));
    }
}
