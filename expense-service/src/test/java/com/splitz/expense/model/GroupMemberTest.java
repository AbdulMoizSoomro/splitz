package com.splitz.expense.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GroupMemberTest {

  @Test
  void builder_ShouldInitializeFields() {
    Group group = Group.builder().id(1L).name("Test Group").build();
    GroupMember member =
        GroupMember.builder().id(10L).group(group).userId(100L).role(GroupRole.ADMIN).build();

    assertEquals(10L, member.getId());
    assertEquals(group, member.getGroup());
    assertEquals(100L, member.getUserId());
    assertEquals(GroupRole.ADMIN, member.getRole());
  }
}
