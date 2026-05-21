package com.splitz.expense.mapper;

import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.model.Payment;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

  public SettlementDTO toSettlementDTO(Payment payment) {
    if (payment == null) {
      return null;
    }

    Long groupId = null;
    if (payment.getAllocations() != null && !payment.getAllocations().isEmpty()) {
      groupId = payment.getAllocations().get(0).getGroupId();
    }

    List<SettlementDTO.AllocationDTO> allocationDTOs = null;
    if (payment.getAllocations() != null) {
      allocationDTOs =
          payment.getAllocations().stream()
              .map(
                  a ->
                      SettlementDTO.AllocationDTO.builder()
                          .groupId(a.getGroupId())
                          .amount(a.getAmount())
                          .build())
              .collect(Collectors.toList());
    }

    return SettlementDTO.builder()
        .id(payment.getId())
        .groupId(groupId)
        .payerId(payment.getPayerId())
        .payeeId(payment.getPayeeId())
        .amount(payment.getAmount())
        .status(payment.getStatus())
        .createdAt(payment.getCreatedAt())
        .updatedAt(payment.getUpdatedAt())
        .markedPaidAt(payment.getMarkedPaidAt())
        .settledAt(payment.getSettledAt())
        .allocations(allocationDTOs)
        .build();
  }

  public FriendshipSettlementDTO toFriendshipSettlementDTO(Payment payment) {
    if (payment == null) {
      return null;
    }

    Long groupId = null;
    if (payment.getAllocations() != null && !payment.getAllocations().isEmpty()) {
      groupId = payment.getAllocations().get(0).getGroupId();
    }

    List<FriendshipSettlementDTO.AllocationDTO> allocationDTOs = null;
    if (payment.getAllocations() != null) {
      allocationDTOs =
          payment.getAllocations().stream()
              .map(
                  a ->
                      FriendshipSettlementDTO.AllocationDTO.builder()
                          .groupId(a.getGroupId())
                          .amount(a.getAmount())
                          .build())
              .collect(Collectors.toList());
    }

    return FriendshipSettlementDTO.builder()
        .id(payment.getId())
        .payerId(payment.getPayerId())
        .payeeId(payment.getPayeeId())
        .groupId(groupId)
        .amount(payment.getAmount())
        .status(payment.getStatus())
        .createdAt(payment.getCreatedAt())
        .updatedAt(payment.getUpdatedAt())
        .markedPaidAt(payment.getMarkedPaidAt())
        .settledAt(payment.getSettledAt())
        .allocations(allocationDTOs)
        .build();
  }

  public List<FriendshipSettlementDTO> toFriendshipSettlementDTOs(List<Payment> payments) {
    if (payments == null) {
      return null;
    }
    return payments.stream().map(this::toFriendshipSettlementDTO).collect(Collectors.toList());
  }

  public List<SettlementDTO> toSettlementDTOs(List<Payment> payments) {
    if (payments == null) {
      return null;
    }
    return payments.stream().map(this::toSettlementDTO).collect(Collectors.toList());
  }
}
