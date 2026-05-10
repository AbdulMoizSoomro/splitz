package com.splitz.expense.service;

import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.BalanceDTO;
import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.model.Expense;
import com.splitz.expense.model.ExpenseSplit;
import com.splitz.expense.model.FriendshipSettlement;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.Settlement;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.FriendshipSettlementRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.SettlementRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BalanceService {

  private final ExpenseRepository expenseRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupRepository groupRepository;
  private final SettlementRepository settlementRepository;
  private final FriendshipSettlementRepository friendshipSettlementRepository;
  private final UserClient userClient;

  @Transactional(readOnly = true)
  public FriendBalanceResponseDTO getNetBalanceWithFriend(Long userId, Long friendId) {
    List<Long> userGroupIds =
        groupMemberRepository.findByUserId(userId).stream()
            .map(gm -> gm.getGroup().getId())
            .collect(Collectors.toList());
    List<Long> friendGroupIds =
        groupMemberRepository.findByUserId(friendId).stream()
            .map(gm -> gm.getGroup().getId())
            .collect(Collectors.toList());

    userGroupIds.retainAll(friendGroupIds);

    BigDecimal netBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (Long groupId : userGroupIds) {
      List<Expense> expenses = expenseRepository.findByGroupId(groupId);
      for (Expense expense : expenses) {
        if (expense.getPaidBy().equals(userId)) {
          for (ExpenseSplit split : expense.getSplits()) {
            if (split.getUserId().equals(friendId)) {
              netBalance = netBalance.add(split.getShareAmount());
            }
          }
        } else if (expense.getPaidBy().equals(friendId)) {
          for (ExpenseSplit split : expense.getSplits()) {
            if (split.getUserId().equals(userId)) {
              netBalance = netBalance.subtract(split.getShareAmount());
            }
          }
        }
      }

      List<Settlement> settlements = settlementRepository.findByGroupId(groupId);
      for (Settlement settlement : settlements) {
        if (settlement.getStatus() == SettlementStatus.COMPLETED) {
          if (settlement.getPayerId().equals(userId) && settlement.getPayeeId().equals(friendId)) {
            netBalance = netBalance.add(settlement.getAmount());
          } else if (settlement.getPayerId().equals(friendId)
              && settlement.getPayeeId().equals(userId)) {
            netBalance = netBalance.subtract(settlement.getAmount());
          }
        }
      }
    }

    List<FriendshipSettlement> globalSettlements =
        friendshipSettlementRepository.findBetweenUsers(userId, friendId);
    for (FriendshipSettlement settlement : globalSettlements) {
      if (settlement.getStatus() == SettlementStatus.COMPLETED) {
        if (settlement.getPayerId().equals(userId)) {
          netBalance = netBalance.add(settlement.getAmount());
        } else {
          netBalance = netBalance.subtract(settlement.getAmount());
        }
      }
    }

    return FriendBalanceResponseDTO.builder()
        .userId(userId)
        .friendId(friendId)
        .netBalance(netBalance)
        .build();
  }

  @Transactional(readOnly = true)
  public GroupBalanceResponseDTO getGroupBalances(Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new ResourceNotFoundException("Group not found with id: " + groupId);
    }

    List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
    List<Expense> expenses = expenseRepository.findByGroupId(groupId);
    List<Settlement> settlements = settlementRepository.findByGroupId(groupId);

    Map<Long, BigDecimal> balances = new HashMap<>();
    for (GroupMember member : members) {
      balances.put(member.getUserId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    for (Expense expense : expenses) {
      Long payerId = expense.getPaidBy();
      BigDecimal amount = expense.getAmount();

      // Add to payer's balance
      balances.put(payerId, balances.getOrDefault(payerId, BigDecimal.ZERO).add(amount));

      // Subtract from each split user's balance
      for (ExpenseSplit split : expense.getSplits()) {
        Long userId = split.getUserId();
        BigDecimal share = split.getShareAmount();
        balances.put(userId, balances.getOrDefault(userId, BigDecimal.ZERO).subtract(share));
      }
    }

    // Process Settlements (only COMPLETED ones)
    for (Settlement settlement : settlements) {
      if (settlement.getStatus() == SettlementStatus.COMPLETED) {
        Long payerId = settlement.getPayerId();
        Long payeeId = settlement.getPayeeId();
        BigDecimal amount = settlement.getAmount();

        // Payer paid off debt, so their balance increases
        balances.put(payerId, balances.getOrDefault(payerId, BigDecimal.ZERO).add(amount));
        // Payee received money, so their balance decreases
        balances.put(payeeId, balances.getOrDefault(payeeId, BigDecimal.ZERO).subtract(amount));
      }
    }

    // Fetch user details for enrichment
    List<Long> userIds = new ArrayList<>(balances.keySet());
    List<UserResponse> userResponses = userClient.getUsersByIds(userIds);
    Map<Long, UserResponse> userMap = new HashMap<>();
    userResponses.forEach(u -> userMap.put(u.getId(), u));

    List<BalanceDTO> balanceDTOs = new ArrayList<>();
    balances.forEach(
        (userId, balance) -> {
          UserResponse user = userMap.get(userId);
          balanceDTOs.add(
              BalanceDTO.builder()
                  .userId(userId)
                  .username(user != null ? user.getUsername() : null)
                  .email(user != null ? user.getEmail() : null)
                  .firstName(user != null ? user.getFirstName() : null)
                  .lastName(user != null ? user.getLastName() : null)
                  .balance(balance)
                  .build());
        });

    List<DebtDTO> simplifiedDebts = simplifyDebts(balances, userMap);

    return GroupBalanceResponseDTO.builder()
        .groupId(groupId)
        .balances(balanceDTOs)
        .simplifiedDebts(simplifiedDebts)
        .build();
  }

  @Transactional(readOnly = true)
  public UserBalanceResponseDTO getUserBalances(Long userId) {
    List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
    List<UserBalanceResponseDTO.GroupBalanceDTO> groupBalances = new ArrayList<>();
    BigDecimal totalBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    for (GroupMember membership : memberships) {
      Long groupId = membership.getGroup().getId();
      String groupName = membership.getGroup().getName();

      // We can optimize this by having a more targeted query, but for now we reuse getGroupBalances
      GroupBalanceResponseDTO groupBalanceResponse = getGroupBalances(groupId);
      BigDecimal userBalance =
          groupBalanceResponse.getBalances().stream()
              .filter(b -> b.getUserId().equals(userId))
              .map(BalanceDTO::getBalance)
              .findFirst()
              .orElse(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));

      groupBalances.add(
          UserBalanceResponseDTO.GroupBalanceDTO.builder()
              .groupId(groupId)
              .groupName(groupName)
              .balance(userBalance)
              .build());

      totalBalance = totalBalance.add(userBalance);
    }

    // Include global friendship settlements
    List<FriendshipSettlement> globalSettlements =
        friendshipSettlementRepository.findByPayerIdOrPayeeId(userId, userId);
    for (FriendshipSettlement settlement : globalSettlements) {
      if (settlement.getStatus() == SettlementStatus.COMPLETED) {
        if (settlement.getPayerId().equals(userId)) {
          totalBalance = totalBalance.add(settlement.getAmount());
        } else {
          totalBalance = totalBalance.subtract(settlement.getAmount());
        }
      }
    }

    UserResponse user = userClient.getUserById(userId).orElse(null);

    return UserBalanceResponseDTO.builder()
        .userId(userId)
        .username(user != null ? user.getUsername() : null)
        .email(user != null ? user.getEmail() : null)
        .totalBalance(totalBalance)
        .groupBalances(groupBalances)
        .build();
  }

  private List<DebtDTO> simplifyDebts(
      Map<Long, BigDecimal> balances, Map<Long, UserResponse> userMap) {
    List<DebtDTO> debts = new ArrayList<>();

    // Creditors (balance > 0) and Debtors (balance < 0)
    PriorityQueue<UserBalance> creditors =
        new PriorityQueue<>((a, b) -> b.amount.compareTo(a.amount));
    PriorityQueue<UserBalance> debtors =
        new PriorityQueue<>((a, b) -> a.amount.compareTo(b.amount));

    balances.forEach(
        (userId, balance) -> {
          if (balance.compareTo(BigDecimal.ZERO) > 0) {
            creditors.add(new UserBalance(userId, balance));
          } else if (balance.compareTo(BigDecimal.ZERO) < 0) {
            debtors.add(new UserBalance(userId, balance));
          }
        });

    while (!creditors.isEmpty() && !debtors.isEmpty()) {
      UserBalance creditor = creditors.poll();
      UserBalance debtor = debtors.poll();

      BigDecimal amountToSettle = creditor.amount.min(debtor.amount.abs());

      UserResponse fromUser = userMap.get(debtor.userId);
      UserResponse toUser = userMap.get(creditor.userId);

      debts.add(
          DebtDTO.builder()
              .from(debtor.userId)
              .fromUsername(fromUser != null ? fromUser.getUsername() : null)
              .to(creditor.userId)
              .toUsername(toUser != null ? toUser.getUsername() : null)
              .amount(amountToSettle.setScale(2, RoundingMode.HALF_UP))
              .build());

      creditor.amount = creditor.amount.subtract(amountToSettle);
      debtor.amount = debtor.amount.add(amountToSettle);

      if (creditor.amount.compareTo(BigDecimal.ZERO) > 0) {
        creditors.add(creditor);
      }
      if (debtor.amount.compareTo(BigDecimal.ZERO) < 0) {
        debtors.add(debtor);
      }
    }

    return debts;
  }

  private static class UserBalance {

    Long userId;
    BigDecimal amount;

    UserBalance(Long userId, BigDecimal amount) {
      this.userId = userId;
      this.amount = amount;
    }
  }
}
