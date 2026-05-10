package com.splitz.expense.service;

import com.splitz.expense.dto.CreateFriendshipSettlementRequest;
import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.exception.UnauthorizedException;
import com.splitz.expense.mapper.FriendshipSettlementMapper;
import com.splitz.expense.model.FriendshipSettlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.FriendshipSettlementRepository;
import java.time.LocalDateTime;
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

  @Transactional
  public FriendshipSettlementDTO createSettlement(CreateFriendshipSettlementRequest request) {
    if (request.getPayerId().equals(request.getPayeeId())) {
      throw new IllegalArgumentException("Payer and payee cannot be the same user");
    }

    FriendshipSettlement settlement =
        FriendshipSettlement.builder()
            .payerId(request.getPayerId())
            .payeeId(request.getPayeeId())
            .amount(request.getAmount())
            .status(SettlementStatus.PENDING)
            .build();

    return friendshipSettlementMapper.toDTO(friendshipSettlementRepository.save(settlement));
  }

  @Transactional(readOnly = true)
  public boolean isInvolved(Long settlementId, Long userId) {
    return friendshipSettlementRepository
        .findById(settlementId)
        .map(s -> s.getPayerId().equals(userId) || s.getPayeeId().equals(userId))
        .orElse(false);
  }

  @Transactional
  public FriendshipSettlementDTO markAsPaid(Long settlementId, Long currentUserId) {
    FriendshipSettlement settlement =
        friendshipSettlementRepository
            .findById(settlementId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Friendship settlement not found with id: " + settlementId));

    if (!settlement.getPayerId().equals(currentUserId)) {
      throw new UnauthorizedException("Only the payer can mark a settlement as paid");
    }

    if (settlement.getStatus() != SettlementStatus.PENDING) {
      throw new IllegalStateException("Settlement must be in PENDING status to be marked as paid");
    }

    settlement.setStatus(SettlementStatus.MARKED_PAID);
    settlement.setMarkedPaidAt(LocalDateTime.now());

    return friendshipSettlementMapper.toDTO(friendshipSettlementRepository.save(settlement));
  }

  @Transactional
  public FriendshipSettlementDTO confirmSettlement(Long settlementId, Long currentUserId) {
    FriendshipSettlement settlement =
        friendshipSettlementRepository
            .findById(settlementId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Friendship settlement not found with id: " + settlementId));

    if (!settlement.getPayeeId().equals(currentUserId)) {
      throw new UnauthorizedException("Only the payee can confirm a settlement");
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
    return friendshipSettlementRepository.findBetweenUsers(userId1, userId2).stream()
        .map(friendshipSettlementMapper::toDTO)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public FriendshipSettlementDTO getSettlementById(Long id) {
    return friendshipSettlementRepository
        .findById(id)
        .map(friendshipSettlementMapper::toDTO)
        .orElseThrow(
            () -> new ResourceNotFoundException("Friendship settlement not found with id: " + id));
  }
}
