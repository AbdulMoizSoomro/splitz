package com.splitz.expense.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GroupTest {

  @Test
  void addMember_ShouldEstablishBiDirectionalRelationship() {
    Group group = Group.builder().name("Test Group").build();
    GroupMember member = GroupMember.builder().userId(1L).role(GroupRole.MEMBER).build();

    group.addMember(member);

    assertTrue(group.getMembers().contains(member));
    assertEquals(group, member.getGroup());
  }

  @Test
  void removeMember_ShouldBreakBiDirectionalRelationship() {
    Group group = Group.builder().name("Test Group").build();
    GroupMember member = GroupMember.builder().userId(1L).role(GroupRole.MEMBER).build();
    group.addMember(member);

    group.removeMember(member);

    assertFalse(group.getMembers().contains(member));
    assertNull(member.getGroup());
  }

  @Test
  void builder_ShouldInitializeFields() {
    Group group =
        Group.builder()
            .id(1L)
            .name("Roommates")
            .description("Shared expenses")
            .imageUrl("http://image.com")
            .createdBy(100L)
            .active(true)
            .build();

    assertEquals(1L, group.getId());
    assertEquals("Roommates", group.getName());
    assertEquals("Shared expenses", group.getDescription());
    assertEquals("http://image.com", group.getImageUrl());
    assertEquals(100L, group.getCreatedBy());
    assertTrue(group.isActive());
  }
}
