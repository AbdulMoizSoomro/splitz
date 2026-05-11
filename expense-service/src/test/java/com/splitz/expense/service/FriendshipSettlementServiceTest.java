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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendshipSettlementServiceTest {

  @Mock private FriendshipSettlementRepository friendshipSettlementRepository;
  @Mock private FriendshipSettlementMapper friendshipSettlementMapper;
  @Mock private com.splitz.security.authorization.SharedSecurityAuthorizer splitzAuthorizer;

  @org.mockito.InjectMocks private FriendshipSettlementService friendshipSettlementService;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn(101L);
  }

  @Test
  void createSettlements_Success() {
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

    when(friendshipSettlementRepository.saveAll(anyList()))
        .thenReturn(Arrays.asList(savedSettlement));
    when(friendshipSettlementMapper.toDTO(savedSettlement)).thenReturn(expectedDto);

    List<FriendshipSettlementDTO> results = friendshipSettlementService.createSettlements(request);

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals(1L, results.get(0).getId());
    verify(friendshipSettlementRepository).saveAll(anyList());
  }

  @Test
  void createSettlements_ManualAllocation_Success() {
    CreateFriendshipSettlementRequest request =
        CreateFriendshipSettlementRequest.builder()
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("100.00"))
            .allocations(
                Arrays.asList(
                    CreateFriendshipSettlementRequest.Allocation.builder()
                        .groupId(1L)
                        .amount(new BigDecimal("40.00"))
                        .build(),
                    CreateFriendshipSettlementRequest.Allocation.builder()
                        .groupId(2L)
                        .amount(new BigDecimal("60.00"))
                        .build()))
            .build();

    when(friendshipSettlementRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    when(friendshipSettlementMapper.toDTO(any(FriendshipSettlement.class)))
        .thenAnswer(
            i -> {
              FriendshipSettlement s = i.getArgument(0);
              return FriendshipSettlementDTO.builder()
                  .groupId(s.getGroupId())
                  .amount(s.getAmount())
                  .build();
            });

    List<FriendshipSettlementDTO> results = friendshipSettlementService.createSettlements(request);

    assertEquals(2, results.size());
    assertEquals(1L, results.get(0).getGroupId());
    assertEquals(new BigDecimal("40.00"), results.get(0).getAmount());
    assertEquals(2L, results.get(1).getGroupId());
    assertEquals(new BigDecimal("60.00"), results.get(1).getAmount());
  }

  @Test
  void createSettlements_ManualAllocation_Mismatch_ThrowsException() {
    CreateFriendshipSettlementRequest request =
        CreateFriendshipSettlementRequest.builder()
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("100.00"))
            .allocations(
                Arrays.asList(
                    CreateFriendshipSettlementRequest.Allocation.builder()
                        .groupId(1L)
                        .amount(new BigDecimal("40.00"))
                        .build(),
                    CreateFriendshipSettlementRequest.Allocation.builder()
                        .groupId(2L)
                        .amount(new BigDecimal("50.00"))
                        .build()))
            .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> friendshipSettlementService.createSettlements(request));
  }

  @Test
  void createSettlements_StatusCompleted_WhenCreatorIsPayee() {
    lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn(102L);
    CreateFriendshipSettlementRequest request =
        CreateFriendshipSettlementRequest.builder()
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .build();

    when(friendshipSettlementRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    when(friendshipSettlementMapper.toDTO(any(FriendshipSettlement.class)))
        .thenAnswer(
            i -> {
              FriendshipSettlement s = i.getArgument(0);
              return FriendshipSettlementDTO.builder().status(s.getStatus()).build();
            });

    // Creator is Payee (102L)
    List<FriendshipSettlementDTO> results = friendshipSettlementService.createSettlements(request);

    assertEquals(SettlementStatus.COMPLETED, results.get(0).getStatus());
  }

  @Test
  void markAsPaid_Success() {
    FriendshipSettlement settlement =
        FriendshipSettlement.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.PENDING)
            .build();

    when(friendshipSettlementRepository.findByIdWithLock(1L)).thenReturn(Optional.of(settlement));
    when(friendshipSettlementRepository.save(any(FriendshipSettlement.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(friendshipSettlementMapper.toDTO(any()))
        .thenAnswer(
            i -> FriendshipSettlementDTO.builder().status(SettlementStatus.MARKED_PAID).build());

    FriendshipSettlementDTO result = friendshipSettlementService.markAsPaid(1L);

    assertNotNull(result);
    assertEquals(SettlementStatus.MARKED_PAID, result.getStatus());
    assertEquals(SettlementStatus.MARKED_PAID, settlement.getStatus());
  }

  @Test
  void markAsPaid_Unauthorized() {
    FriendshipSettlement settlement =
        FriendshipSettlement.builder().id(1L).payerId(101L).payeeId(102L).build();

    when(friendshipSettlementRepository.findByIdWithLock(1L)).thenReturn(Optional.of(settlement));
    lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn(999L);
    lenient().when(splitzAuthorizer.isAdmin()).thenReturn(false);

    assertThrows(
        com.splitz.expense.exception.UnauthorizedException.class,
        () -> friendshipSettlementService.markAsPaid(1L));
  }

  @Test
  void confirmSettlement_Success() {
    lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn(102L);
    FriendshipSettlement settlement =
        FriendshipSettlement.builder()
            .id(1L)
            .payerId(101L)
            .payeeId(102L)
            .amount(new BigDecimal("50.00"))
            .status(SettlementStatus.MARKED_PAID)
            .build();

    when(friendshipSettlementRepository.findByIdWithLock(1L)).thenReturn(Optional.of(settlement));
    when(friendshipSettlementRepository.save(any(FriendshipSettlement.class)))
        .thenAnswer(i -> i.getArguments()[0]);
    when(friendshipSettlementMapper.toDTO(any()))
        .thenAnswer(
            i -> FriendshipSettlementDTO.builder().status(SettlementStatus.COMPLETED).build());

    FriendshipSettlementDTO result = friendshipSettlementService.confirmSettlement(1L);

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
    lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn(999L);
    lenient().when(splitzAuthorizer.isAdmin()).thenReturn(false);

    assertThrows(
        com.splitz.expense.exception.UnauthorizedException.class,
        () -> friendshipSettlementService.getSettlementById(1L));
  }
}
