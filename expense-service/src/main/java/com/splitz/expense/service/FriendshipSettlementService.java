package com.splitz.expense.service;

import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.mapper.FriendshipSettlementMapper;
import com.splitz.expense.model.FriendshipSettlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.FriendshipSettlementRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendshipSettlementService {

  private final FriendshipSettlementRepository friendshipSettlementRepository;
  private final FriendshipSettlementMapper friendshipSettlementMapper;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @Transactional
  public List<FriendshipSettlementDTO> createSettlements(
      CreateFriendshipSettlementRequest request) {
    if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("Settlement amount must be positive");
    }
    if (request.getPayerId().equals(request.getPayeeId())) {
      throw new IllegalArgumentException("Payer and payee cannot be the same user");
    }

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(request.getPayerId())
        && !currentUserId.equals(request.getPayeeId())
        && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to create this settlement");
    }

    SettlementStatus status = SettlementStatus.PENDING;
    LocalDateTime markedPaidAt = null;
    LocalDateTime settledAt = null;

    if (currentUserId.equals(request.getPayeeId())) {
      status = SettlementStatus.COMPLETED;
      settledAt = LocalDateTime.now();
    } else if (currentUserId.equals(request.getPayerId())) {
      status = SettlementStatus.MARKED_PAID;
      markedPaidAt = LocalDateTime.now();
    }

    List<FriendshipSettlement> settlements = new ArrayList<>();

    if (request.getAllocations() != null && !request.getAllocations().isEmpty()) {
      BigDecimal totalAllocated =
          request.getAllocations().stream()
              .map(CreateFriendshipSettlementRequest.Allocation::getAmount)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      if (totalAllocated.compareTo(request.getAmount()) != 0) {
        throw new IllegalArgumentException(
            "Total allocated amount ("
                + totalAllocated
                + ") must match settlement amount ("
                + request.getAmount()
                + ")");
      }

      for (CreateFriendshipSettlementRequest.Allocation allocation : request.getAllocations()) {
        settlements.add(
            FriendshipSettlement.builder()
                .payerId(request.getPayerId())
                .payeeId(request.getPayeeId())
                .groupId(allocation.getGroupId())
                .amount(allocation.getAmount())
                .status(status)
                .markedPaidAt(markedPaidAt)
                .settledAt(settledAt)
                .build());
      }
    } else {
      settlements.add(
          FriendshipSettlement.builder()
              .payerId(request.getPayerId())
              .payeeId(request.getPayeeId())
              .groupId(request.getGroupId())
              .amount(request.getAmount())
              .status(status)
              .markedPaidAt(markedPaidAt)
              .settledAt(settledAt)
              .build());
    }

    return friendshipSettlementRepository.saveAll(settlements).stream()
        .map(friendshipSettlementMapper::toDTO)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public boolean isParticipant(Long settlementId) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return friendshipSettlementRepository
        .findById(settlementId)
        .map(s -> s.getPayerId().equals(currentUserId) || s.getPayeeId().equals(currentUserId))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean isPayer(Long settlementId) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return friendshipSettlementRepository
        .findById(settlementId)
        .map(s -> s.getPayerId().equals(currentUserId))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean isPayee(Long settlementId) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return friendshipSettlementRepository
        .findById(settlementId)
        .map(s -> s.getPayeeId().equals(currentUserId))
        .orElse(false);
  }

  @Transactional
  public FriendshipSettlementDTO markAsPaid(Long settlementId) {
    FriendshipSettlement settlement =
        friendshipSettlementRepository
            .findByIdWithLock(settlementId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Friendship settlement not found with id: " + settlementId));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(settlement.getPayerId()) && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to mark this settlement as paid");
    }

    if (settlement.getStatus() != SettlementStatus.PENDING) {
      throw new IllegalStateException("Settlement must be in PENDING status to be marked as paid");
    }

    settlement.setStatus(SettlementStatus.MARKED_PAID);
    settlement.setMarkedPaidAt(LocalDateTime.now());

    return friendshipSettlementMapper.toDTO(friendshipSettlementRepository.save(settlement));
  }

  @Transactional
  public FriendshipSettlementDTO confirmSettlement(Long settlementId) {
    FriendshipSettlement settlement =
        friendshipSettlementRepository
            .findByIdWithLock(settlementId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Friendship settlement not found with id: " + settlementId));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(settlement.getPayeeId()) && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to confirm this settlement");
    }

    if (settlement.getStatus() != SettlementStatus.MARKED_PAID) {
      throw new IllegalStateException("Settlement must be in MARKED_PAID status to be confirmed");
    }

    settlement.setStatus(SettlementStatus.COMPLETED);
    settlement.setSettledAt(LocalDateTime.now());

    return friendshipSettlementMapper.toDTO(friendshipSettlementRepository.save(settlement));
  }

  @Transactional(readOnly = true)
  public List<FriendshipSettlementDTO> getSettlementsBetweenUsers(Long userId1, Long userId2) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(userId1)
        && !currentUserId.equals(userId2)
        && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to view these settlements");
    }
    return friendshipSettlementRepository.findBetweenUsers(userId1, userId2).stream()
        .map(friendshipSettlementMapper::toDTO)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public FriendshipSettlementDTO getSettlementById(Long id) {
    FriendshipSettlement settlement =
        friendshipSettlementRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Friendship settlement not found with id: " + id));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(settlement.getPayerId())
        && !currentUserId.equals(settlement.getPayeeId())
        && !splitzAuthorizer.isAdmin()) {
      throw new UnauthorizedException("You are not authorized to view this settlement");
    }

    return friendshipSettlementMapper.toDTO(settlement);
  }
}
