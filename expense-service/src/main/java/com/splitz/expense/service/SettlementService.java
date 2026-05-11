package com.splitz.expense.service;

import com.splitz.expense.dto.CreateSettlementRequest;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.mapper.SettlementMapper;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.Settlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementRepository;
import com.splitz.security.authorization.SharedSecurityAuthorizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementService {

  private final SettlementRepository settlementRepository;
  private final GroupRepository groupRepository;
  private final SettlementMapper settlementMapper;
  private final SharedSecurityAuthorizer splitzAuthorizer;

  @Transactional
  public SettlementDTO createSettlement(CreateSettlementRequest request) {
    Group group =
        groupRepository
            .findById(request.getGroupId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Group not found with id: " + request.getGroupId()));

    SettlementStatus status = SettlementStatus.PENDING;
    LocalDateTime markedPaidAt = null;
    LocalDateTime settledAt = null;

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (currentUserId.equals(request.getPayeeId())) {
      status = SettlementStatus.COMPLETED;
      settledAt = LocalDateTime.now();
    } else if (currentUserId.equals(request.getPayerId())) {
      status = SettlementStatus.MARKED_PAID;
      markedPaidAt = LocalDateTime.now();
    }

    Settlement settlement =
        Settlement.builder()
            .group(group)
            .payerId(request.getPayerId())
            .payeeId(request.getPayeeId())
            .amount(request.getAmount())
            .status(status)
            .markedPaidAt(markedPaidAt)
            .settledAt(settledAt)
            .build();

    return settlementMapper.toDTO(settlementRepository.save(settlement));
  }

  @Transactional
  public SettlementDTO markAsPaid(Long settlementId) {
    Settlement settlement =
        settlementRepository
            .findByIdWithLock(settlementId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Settlement not found with id: " + settlementId));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(settlement.getPayerId()) && !splitzAuthorizer.isAdmin()) {
      throw new com.splitz.expense.exception.UnauthorizedException(
          "You are not authorized to mark this settlement as paid");
    }

    if (settlement.getStatus() != SettlementStatus.PENDING) {
      throw new IllegalStateException("Settlement must be in PENDING status to be marked as paid");
    }

    settlement.setStatus(SettlementStatus.MARKED_PAID);
    settlement.setMarkedPaidAt(LocalDateTime.now());

    return settlementMapper.toDTO(settlementRepository.save(settlement));
  }

  @Transactional
  public SettlementDTO confirmSettlement(Long settlementId) {
    Settlement settlement =
        settlementRepository
            .findByIdWithLock(settlementId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException("Settlement not found with id: " + settlementId));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(settlement.getPayeeId()) && !splitzAuthorizer.isAdmin()) {
      throw new com.splitz.expense.exception.UnauthorizedException(
          "You are not authorized to confirm this settlement");
    }

    if (settlement.getStatus() != SettlementStatus.MARKED_PAID) {
      throw new IllegalStateException("Settlement must be in MARKED_PAID status to be confirmed");
    }

    settlement.setStatus(SettlementStatus.COMPLETED);
    settlement.setSettledAt(LocalDateTime.now());

    return settlementMapper.toDTO(settlementRepository.save(settlement));
  }

  @Transactional(readOnly = true)
  public boolean isParticipant(Long settlementId) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return settlementRepository
        .findById(settlementId)
        .map(s -> s.getPayerId().equals(currentUserId) || s.getPayeeId().equals(currentUserId))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean isPayer(Long settlementId) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return settlementRepository
        .findById(settlementId)
        .map(s -> s.getPayerId().equals(currentUserId))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public boolean isPayee(Long settlementId) {
    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    return settlementRepository
        .findById(settlementId)
        .map(s -> s.getPayeeId().equals(currentUserId))
        .orElse(false);
  }

  @Transactional(readOnly = true)
  public List<SettlementDTO> getSettlementsByGroup(Long groupId) {
    return settlementRepository.findByGroupId(groupId).stream()
        .map(settlementMapper::toDTO)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public SettlementDTO getSettlementById(Long id) {
    Settlement settlement =
        settlementRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Settlement not found with id: " + id));

    Long currentUserId = splitzAuthorizer.getCurrentUserId();
    if (!currentUserId.equals(settlement.getPayerId())
        && !currentUserId.equals(settlement.getPayeeId())
        && !splitzAuthorizer.isAdmin()) {
      throw new com.splitz.expense.exception.UnauthorizedException(
          "You are not authorized to view this settlement");
    }

    return settlementMapper.toDTO(settlement);
  }
}
