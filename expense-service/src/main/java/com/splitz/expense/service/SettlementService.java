package com.splitz.expense.service;

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
    private final GroupMemberRepository groupMemberRepository;
    private final SettlementMapper settlementMapper;

    @Transactional
    public SettlementDTO createSettlement(CreateSettlementRequest request) {
        Group group
                = groupRepository
                        .findById(request.getGroupId())
                        .orElseThrow(
                                ()
                                -> new ResourceNotFoundException(
                                        "Group not found with id: " + request.getGroupId()));

        if (!groupMemberRepository.existsByGroupIdAndUserId(
                request.getGroupId(), request.getPayerId())) {
            throw new ResourceNotFoundException("Payer is not a member of the group");
        }
        if (!groupMemberRepository.existsByGroupIdAndUserId(
                request.getGroupId(), request.getPayeeId())) {
            throw new ResourceNotFoundException("Payee is not a member of the group");
        }

        Settlement settlement
                = Settlement.builder()
                        .group(group)
                        .payerId(request.getPayerId())
                        .payeeId(request.getPayeeId())
                        .amount(request.getAmount())
                        .status(SettlementStatus.PENDING)
                        .build();

        return settlementMapper.toDTO(settlementRepository.save(settlement));
    }

    @Transactional
    public SettlementDTO markAsPaid(Long settlementId, Long currentUserId) {
        Settlement settlement
                = settlementRepository
                        .findById(settlementId)
                        .orElseThrow(
                                ()
                                -> new ResourceNotFoundException("Settlement not found with id: " + settlementId));

        if (!settlement.getPayerId().equals(currentUserId)) {
            throw new UnauthorizedException("Only the payer can mark a settlement as paid");
        }

        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new IllegalStateException("Settlement must be in PENDING status to be marked as paid");
        }

        settlement.setStatus(SettlementStatus.MARKED_PAID);
        settlement.setMarkedPaidAt(LocalDateTime.now());

        return settlementMapper.toDTO(settlementRepository.save(settlement));
    }

    @Transactional
    public SettlementDTO confirmSettlement(Long settlementId, Long currentUserId) {
        Settlement settlement
                = settlementRepository
                        .findById(settlementId)
                        .orElseThrow(
                                ()
                                -> new ResourceNotFoundException("Settlement not found with id: " + settlementId));

        if (!settlement.getPayeeId().equals(currentUserId)) {
            throw new UnauthorizedException("Only the payee can confirm a settlement");
        }

        if (settlement.getStatus() != SettlementStatus.MARKED_PAID) {
            throw new IllegalStateException("Settlement must be in MARKED_PAID status to be confirmed");
        }

        settlement.setStatus(SettlementStatus.COMPLETED);
        settlement.setSettledAt(LocalDateTime.now());

        return settlementMapper.toDTO(settlementRepository.save(settlement));
    }

    @Transactional(readOnly = true)
    public List<SettlementDTO> getSettlementsByGroup(Long groupId) {
        return settlementRepository.findByGroupId(groupId).stream()
                .map(settlementMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SettlementDTO getSettlementById(Long id) {
        return settlementRepository
                .findById(id)
                .map(settlementMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found with id: " + id));
    }
}
