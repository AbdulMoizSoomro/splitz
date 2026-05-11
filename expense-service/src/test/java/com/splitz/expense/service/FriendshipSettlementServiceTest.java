package com.splitz.expense.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.mapper.FriendshipSettlementMapper;
import com.splitz.expense.model.FriendshipSettlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.FriendshipSettlementRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendshipSettlementServiceTest {

  @Mock private FriendshipSettlementRepository friendshipSettlementRepository;
  @Mock private FriendshipSettlementMapper friendshipSettlementMapper;
  @Mock private com.splitz.security.authorization.SharedSecurityAuthorizer splitzAuthorizer;

  @InjectMocks private FriendshipSettlementService friendshipSettlementService;

  @Test
  void createSettlement_Success() {
    CreateFriendshipSettlementRequest request =
        CreateFriendshipSettlementRequest.builder()
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .build();

    FriendshipSettlement savedSettlement =
        FriendshipSettlement.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();

    FriendshipSettlementDTO expectedDto =
        FriendshipSettlementDTO.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(friendshipSettlementRepository.save(any(FriendshipSettlement.class)))
        .thenReturn(savedSettlement);
    when(friendshipSettlementMapper.toDTO(savedSettlement)).thenReturn(expectedDto);

    FriendshipSettlementDTO result = friendshipSettlementService.createSettlement(request, 101L);

    assertNotNull(result);
    assertEquals(1L, result.getId());
    assertEquals(SettlementStatus.PENDING, result.getStatus());
    verify(friendshipSettlementRepository).save(any(FriendshipSettlement.class));
  }

  @Test
  void confirmSettlement_Success() {
    FriendshipSettlement settlement =
        FriendshipSettlement.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.MARKED_PAID)
            .build();

    when(friendshipSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
    when(friendshipSettlementRepository.save(any(FriendshipSettlement.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(friendshipSettlementMapper.toDTO(any()))
        .thenAnswer(
            i -> FriendshipSettlementDTO.builder().status(SettlementStatus.COMPLETED).build());

    FriendshipSettlementDTO result = friendshipSettlementService.confirmSettlement(1L, 102L);

    assertNotNull(result);
    assertEquals(SettlementStatus.COMPLETED, result.getStatus());
    assertEquals(SettlementStatus.COMPLETED, settlement.getStatus());
  }

  @Test
  void getSettlementById_Unauthorized() {
    FriendshipSettlement settlement =
        FriendshipSettlement.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(friendshipSettlementRepository.findById(1L)).thenReturn(Optional.of(settlement));
    when(splitzAuthorizer.isAdmin()).thenReturn(false);

    assertThrows(
        com.splitz.expense.exception.UnauthorizedException.class,
        () -> friendshipSettlementService.getSettlementById(1L, 999L));
  }
}
