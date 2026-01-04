package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.mapper.SettlementMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.Settlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

  @Mock private SettlementRepository settlementRepository;

  @Mock private GroupRepository groupRepository;

  @Mock private GroupMemberRepository groupMemberRepository;

  @Mock private SettlementMapper settlementMapper;

  @InjectMocks private SettlementService settlementService;

  private Group group;
  private CreateSettlementRequest request;
  private Settlement settlement;
  private SettlementDTO settlementDTO;

  @BeforeEach
  void setUp() {
    group = Group.builder().id(1L).name("Test Group").build();
    request =
        CreateSettlementRequest.builder()
            .groupId(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .build();

    settlement =
        Settlement.builder()
            .id(1L)
            .group(group)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();

    settlementDTO =
        SettlementDTO.builder()
            .id(1L)
            .groupId(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();
  }

  @Test
  void createSettlement_Success() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 101L)).thenReturn(true);
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 102L)).thenReturn(true);
    when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);
    when(settlementMapper.toDTO(any(Settlement.class))).thenReturn(settlementDTO);

    SettlementDTO result = settlementService.createSettlement(request);

    assertNotNull(result);
    assertEquals(SettlementStatus.PENDING, result.getStatus());
    verify(settlementRepository).save(any(Settlement.class));
  }

  @Test
  void createSettlement_GroupNotFound() {
    when(groupRepository.findById(1L)).thenReturn(Optional.empty());

    assertThrows(
        ResourceNotFoundException.class, () -> settlementService.createSettlement(request));
  }

  @Test
  void createSettlement_PayerNotMember() {
    when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
    when(groupMemberRepository.existsByGroupIdAndUserId(1L, 101L)).thenReturn(false);

    assertThrows(
        ResourceNotFoundException.class, () -> settlementService.createSettlement(request));
  }

  @Test
  void markAsPaid_Success() {
    when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
    when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);
    when(settlementMapper.toDTO(any(Settlement.class))).thenReturn(settlementDTO);

    settlementDTO.setStatus(SettlementStatus.MARKED_PAID);
    SettlementDTO result = settlementService.markAsPaid(1L, 101L);

    assertNotNull(result);
    assertEquals(SettlementStatus.MARKED_PAID, result.getStatus());
    verify(settlementRepository).save(settlement);
  }

  @Test
  void markAsPaid_Unauthorized() {
    when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

    assertThrows(UnauthorizedException.class, () -> settlementService.markAsPaid(1L, 999L));
  }

  @Test
  void markAsPaid_InvalidStatus() {
    settlement.setStatus(SettlementStatus.COMPLETED);
    when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

    assertThrows(IllegalStateException.class, () -> settlementService.markAsPaid(1L, 101L));
  }

  @Test
  void confirmSettlement_Success() {
    settlement.setStatus(SettlementStatus.MARKED_PAID);
    when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
    when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);
    when(settlementMapper.toDTO(any(Settlement.class))).thenReturn(settlementDTO);

    settlementDTO.setStatus(SettlementStatus.COMPLETED);
    SettlementDTO result = settlementService.confirmSettlement(1L, 102L);

    assertNotNull(result);
    assertEquals(SettlementStatus.COMPLETED, result.getStatus());
    verify(settlementRepository).save(settlement);
  }

  @Test
  void confirmSettlement_Unauthorized() {
    settlement.setStatus(SettlementStatus.MARKED_PAID);
    when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

    assertThrows(UnauthorizedException.class, () -> settlementService.confirmSettlement(1L, 101L));
  }

  @Test
  void confirmSettlement_InvalidStatus() {
    settlement.setStatus(SettlementStatus.PENDING);
    when(settlementRepository.findById(1L)).thenReturn(Optional.of(settlement));

    assertThrows(IllegalStateException.class, () -> settlementService.confirmSettlement(1L, 102L));
  }
}
