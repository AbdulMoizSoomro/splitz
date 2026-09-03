package com.splitz.expense.mapper;

import com.splitz.expense.dto.FriendshipSettlementDTO;
import com.splitz.expense.dto.SettlementDTO;
import com.splitz.expense.model.Payment;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

  public SettlementDTO toSettlementDTO(Payment payment) {
    if (payment == null) {
      return null;
    }

    return SettlementDTO.builder()
        .id(payment.getId())
        .type(payment.getType())
        .groupId(payment.getGroupId())
        .payerId(payment.getPayerId())
        .payeeId(payment.getPayeeId())
        .amount(payment.getAmount())
        .status(payment.getStatus())
        .notes(payment.getNotes())
        .createdAt(payment.getCreatedAt())
        .updatedAt(payment.getUpdatedAt())
        .markedPaidAt(payment.getMarkedPaidAt())
        .settledAt(payment.getSettledAt())
        .allocations(Collections.emptyList())
        .build();
  }

  public FriendshipSettlementDTO toFriendshipSettlementDTO(Payment payment) {
    if (payment == null) {
      return null;
    }

    return FriendshipSettlementDTO.builder()
        .id(payment.getId())
        .type(payment.getType())
        .payerId(payment.getPayerId())
        .payeeId(payment.getPayeeId())
        .groupId(payment.getGroupId())
        .amount(payment.getAmount())
        .status(payment.getStatus())
        .notes(payment.getNotes())
        .createdAt(payment.getCreatedAt())
        .updatedAt(payment.getUpdatedAt())
        .markedPaidAt(payment.getMarkedPaidAt())
        .settledAt(payment.getSettledAt())
        .allocations(Collections.emptyList())
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
