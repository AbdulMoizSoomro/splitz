package com.splitz.expense.mapper;

import com.splitz.expense.dto.GroupDTO;
import com.splitz.expense.dto.GroupMemberDTO;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Manual mapper for groups to keep test/runtime classpath simple. */
@Component
public class GroupMapper {

  private final GroupMemberMapper groupMemberMapper;

  public GroupMapper(GroupMemberMapper groupMemberMapper) {
    this.groupMemberMapper = groupMemberMapper;
  }

  public GroupDTO toDTO(Group group) {
    if (group == null) {
      return null;
    }

    return GroupDTO.builder()
        .id(group.getId())
        .name(group.getName())
        .description(group.getDescription())
        .imageUrl(group.getImageUrl())
        .createdBy(group.getCreatedBy())
        .active(group.isActive())
        .createdAt(group.getCreatedAt())
        .updatedAt(group.getUpdatedAt())
        .members(mapMembers(group.getMembers()))
        .build();
  }

  private List<GroupMemberDTO> mapMembers(Set<GroupMember> members) {
    if (members == null) {
      return null;
    }

    List<GroupMemberDTO> result = new ArrayList<>(members.size());
    for (GroupMember member : members) {
      result.add(groupMemberMapper.toDTO(member));
    }
    return result;
  }
}
