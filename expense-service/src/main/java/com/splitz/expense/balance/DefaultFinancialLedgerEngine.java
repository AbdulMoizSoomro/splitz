package com.splitz.expense.balance;

import com.splitz.expense.balancesource.NetBalanceResult;
import com.splitz.expense.client.UserClient;
import com.splitz.expense.dto.BalanceDTO;
import com.splitz.expense.dto.CounterpartyResponseDTO;
import com.splitz.expense.dto.DebtDTO;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.FriendGroupBalanceDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import com.splitz.expense.dto.UserResponse;
import com.splitz.expense.exception.ResourceNotFoundException;
import com.splitz.expense.model.Group;
import com.splitz.expense.model.GroupMember;
import com.splitz.expense.model.GroupSimplificationSettings;
import com.splitz.expense.model.Payment;
import com.splitz.expense.model.SettlementStatus;
import com.splitz.expense.netting.DebtProjectionEngine;
import com.splitz.expense.repository.ExpenseRepository;
import com.splitz.expense.repository.GroupMemberRepository;
import com.splitz.expense.repository.GroupRepository;
import com.splitz.expense.repository.LedgerRepository;
import com.splitz.expense.repository.PaymentRepository;
import com.splitz.expense.repository.UserBalanceAggregate;
import com.splitz.expense.repository.UserGroupAggregate;
import com.splitz.expense.service.GroupSimplificationSettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deep implementation of the {@link FinancialLedgerEngine} module.
 *
 * <p>Consolidates balance calculations, cross-group aggregations, debt netting, and counterparty
 * hydration behind a single high-leverage interface. Hides SQL aggregations, membership graphs, and
 * profile enrichment from callers.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultFinancialLedgerEngine implements FinancialLedgerEngine {

  private static final List<SettlementStatus> SETTLEMENT_STATUSES =
      List.of(SettlementStatus.COMPLETED, SettlementStatus.MARKED_PAID);

  private static final BigDecimal BALANCE_TOLERANCE = new BigDecimal("0.01");

  private final ExpenseRepository expenseRepository;
  private final GroupMemberRepository groupMemberRepository;
  private final GroupRepository groupRepository;
  private final PaymentRepository paymentRepository;
  private final UserClient userClient;
  private final DebtProjectionEngine debtProjectionEngine;
  private final GroupSimplificationSettingsService settingsService;
  private final LedgerRepository ledgerRepository;

  @Override
  public List<CounterpartyResponseDTO> getCounterparties(Long userId) {
    List<GroupMember> userMemberships = groupMemberRepository.findByUserId(userId);
    if (userMemberships.isEmpty()) {
      return Collections.emptyList();
    }

    Map<Long, String> groupNamesById =
        userMemberships.stream()
            .map(GroupMember::getGroup)
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Group::getId, Group::getName, (g1, g2) -> g1));

    Set<Long> userGroupIds = groupNamesById.keySet();
    Map<Long, BigDecimal> counterpartyBalances = new HashMap<>();
    Map<Long, Set<Long>> counterpartyGroupIds = new HashMap<>();

    accumulateGroupCounterpartyBalances(
        userId, userGroupIds, counterpartyBalances, counterpartyGroupIds);

    // Incorporate direct/global settlements between userId and other users
    accumulateGlobalSettlements(userId, counterpartyBalances);

    // Filter out zero-balance positions (|balance| < 0.01)
    List<Long> activeCounterpartyIds =
        counterpartyBalances.entrySet().stream()
            .filter(e -> e.getValue().abs().compareTo(BALANCE_TOLERANCE) >= 0)
            .map(Map.Entry::getKey)
            .sorted()
            .collect(Collectors.toList());

    if (activeCounterpartyIds.isEmpty()) {
      return Collections.emptyList();
    }

    List<UserResponse> userResponses = userClient.getUsersByIds(activeCounterpartyIds);
    Map<Long, UserResponse> userMap =
        userResponses != null
            ? userResponses.stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(UserResponse::getId, u -> u, (u1, u2) -> u1))
            : Collections.emptyMap();

    List<CounterpartyResponseDTO> result = new ArrayList<>();
    for (Long counterpartyUserId : activeCounterpartyIds) {
      UserResponse user = userMap.get(counterpartyUserId);
      BigDecimal balance =
          counterpartyBalances.get(counterpartyUserId).setScale(2, RoundingMode.HALF_UP);

      List<CounterpartyResponseDTO.GroupRefDTO> groupRefs =
          counterpartyGroupIds.getOrDefault(counterpartyUserId, Collections.emptySet()).stream()
              .map(
                  gid ->
                      CounterpartyResponseDTO.GroupRefDTO.builder()
                          .id(gid)
                          .name(groupNamesById.getOrDefault(gid, "Group " + gid))
                          .build())
              .sorted((g1, g2) -> g1.getId().compareTo(g2.getId()))
              .collect(Collectors.toList());

      result.add(
          CounterpartyResponseDTO.builder()
              .userId(counterpartyUserId)
              .username(user != null ? user.getUsername() : null)
              .firstName(user != null ? user.getFirstName() : null)
              .lastName(user != null ? user.getLastName() : null)
              .email(user != null ? user.getEmail() : null)
              .balance(balance)
              .groups(groupRefs)
              .build());
    }

    return result;
  }

  @Override
  public GroupBalanceResponseDTO getGroupBalances(Long groupId) {
    if (!groupRepository.existsById(groupId)) {
      throw new ResourceNotFoundException("Group not found with id: " + groupId);
    }

    List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
    List<Long> memberIds = members.stream().map(GroupMember::getUserId).toList();

    Map<Long, BigDecimal> netBalances = calculateGroupBalances(groupId, memberIds);

    List<UserResponse> userResponses = userClient.getUsersByIds(memberIds);
    Map<Long, UserResponse> userMap =
        userResponses != null
            ? userResponses.stream()
                .filter(u -> u.getId() != null)
                .collect(Collectors.toMap(UserResponse::getId, u -> u, (u1, u2) -> u1))
            : Collections.emptyMap();

    List<BalanceDTO> balanceDTOs =
        netBalances.entrySet().stream()
            .map(
                entry -> {
                  UserResponse user = userMap.get(entry.getKey());
                  return BalanceDTO.builder()
                      .userId(entry.getKey())
                      .username(user != null ? user.getUsername() : null)
                      .email(user != null ? user.getEmail() : null)
                      .firstName(user != null ? user.getFirstName() : null)
                      .lastName(user != null ? user.getLastName() : null)
                      .balance(entry.getValue())
                      .build();
                })
            .collect(Collectors.toList());

    GroupSimplificationSettings settings = settingsService.readSettings(groupId);

    List<DebtDTO> debtDTOs;
    if (!settings.isSimplificationEnabled()) {
      debtDTOs = calculateRawDebts(groupId, memberIds, userMap);
    } else {
      Map<Long, String> usernames = new HashMap<>();
      userMap.forEach((userId, user) -> usernames.put(userId, user.getUsername()));
      debtDTOs = projectGroupDebts(groupId, memberIds, netBalances, settings, usernames).debts();
    }

    return GroupBalanceResponseDTO.builder()
        .groupId(groupId)
        .balances(balanceDTOs)
        .simplifiedDebts(debtDTOs)
        .build();
  }

  private List<DebtDTO> calculateRawDebts(
      Long groupId, List<Long> memberIds, Map<Long, UserResponse> userMap) {
    List<DebtDTO> debts = new ArrayList<>();
    if (memberIds == null || memberIds.size() < 2) {
      return debts;
    }

    List<Long> sortedMemberIds = new ArrayList<>(memberIds);
    Collections.sort(sortedMemberIds);

    for (int i = 0; i < sortedMemberIds.size(); i++) {
      Long userA = sortedMemberIds.get(i);
      for (int j = i + 1; j < sortedMemberIds.size(); j++) {
        Long userB = sortedMemberIds.get(j);

        BigDecimal net = ledgerRepository.calculatePairwiseBalanceInGroup(userA, userB, groupId);
        if (net != null && net.compareTo(BALANCE_TOLERANCE) >= 0) {
          UserResponse fromUser = userMap.get(userB);
          UserResponse toUser = userMap.get(userA);
          debts.add(
              DebtDTO.builder()
                  .from(userB)
                  .fromUsername(fromUser != null ? fromUser.getUsername() : null)
                  .to(userA)
                  .toUsername(toUser != null ? toUser.getUsername() : null)
                  .amount(net.setScale(2, RoundingMode.HALF_UP))
                  .build());
        } else if (net != null && net.negate().compareTo(BALANCE_TOLERANCE) >= 0) {
          UserResponse fromUser = userMap.get(userA);
          UserResponse toUser = userMap.get(userB);
          debts.add(
              DebtDTO.builder()
                  .from(userA)
                  .fromUsername(fromUser != null ? fromUser.getUsername() : null)
                  .to(userB)
                  .toUsername(toUser != null ? toUser.getUsername() : null)
                  .amount(net.negate().setScale(2, RoundingMode.HALF_UP))
                  .build());
        }
      }
    }
    return debts;
  }

  private void accumulateGroupCounterpartyBalances(
      Long userId,
      Set<Long> userGroupIds,
      Map<Long, BigDecimal> counterpartyBalances,
      Map<Long, Set<Long>> counterpartyGroupIds) {
    for (Long groupId : userGroupIds) {
      List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
      List<Long> memberIds = members.stream().map(GroupMember::getUserId).toList();

      GroupSimplificationSettings settings = settingsService.readSettings(groupId);

      if (!settings.isSimplificationEnabled()) {
        for (Long memberId : memberIds) {
          if (!memberId.equals(userId)) {
            BigDecimal pairwiseBalance =
                ledgerRepository.calculatePairwiseBalanceInGroup(userId, memberId, groupId);
            if (pairwiseBalance != null
                && pairwiseBalance.abs().compareTo(BALANCE_TOLERANCE) >= 0) {
              counterpartyBalances.put(
                  memberId,
                  counterpartyBalances
                      .getOrDefault(memberId, BigDecimal.ZERO)
                      .add(pairwiseBalance));
              counterpartyGroupIds.computeIfAbsent(memberId, k -> new HashSet<>()).add(groupId);
            }
          }
        }
      } else {
        DebtProjectionEngine.Projection projection =
            projectGroupDebts(
                groupId,
                memberIds,
                calculateGroupBalances(groupId, memberIds),
                settings,
                Collections.emptyMap());
        for (DebtDTO debt : projection.debts()) {
          Long otherId = null;
          BigDecimal delta = BigDecimal.ZERO;
          if (debt.getFrom().equals(userId)) {
            otherId = debt.getTo();
            delta = debt.getAmount().negate();
          } else if (debt.getTo().equals(userId)) {
            otherId = debt.getFrom();
            delta = debt.getAmount();
          }
          if (otherId != null && !otherId.equals(userId)) {
            counterpartyBalances.merge(otherId, delta, BigDecimal::add);
            counterpartyGroupIds.computeIfAbsent(otherId, k -> new HashSet<>()).add(groupId);
          }
        }
      }
    }
  }

  private DebtProjectionEngine.Projection projectGroupDebts(
      Long groupId,
      List<Long> memberIds,
      Map<Long, BigDecimal> netBalances,
      GroupSimplificationSettings settings,
      Map<Long, String> usernames) {
    NetBalanceResult balances =
        NetBalanceResult.builder()
            .netBalances(netBalances)
            .groupIdsByUser(
                memberIds.stream().collect(Collectors.toMap(id -> id, id -> Set.of(groupId))))
            .originalTransactionCount(
                (int)
                    (expenseRepository.countByGroupId(groupId)
                        + paymentRepository.countByGroupId(groupId)))
            .build();
    return debtProjectionEngine.project(
        groupId, memberIds, settings.getOptOutUserIds(), balances, usernames);
  }

  private void accumulateGlobalSettlements(
      Long userId, Map<Long, BigDecimal> counterpartyBalances) {
    List<Payment> directPayments =
        paymentRepository.findDirectPaymentsForUser(userId, SETTLEMENT_STATUSES);
    if (directPayments != null) {
      for (Payment payment : directPayments) {
        Long payerId = payment.getPayerId();
        Long payeeId = payment.getPayeeId();
        Long otherId = payerId.equals(userId) ? payeeId : payerId;
        if (otherId != null && !otherId.equals(userId)) {
          BigDecimal delta =
              payerId.equals(userId) ? payment.getAmount() : payment.getAmount().negate();
          counterpartyBalances.put(
              otherId, counterpartyBalances.getOrDefault(otherId, BigDecimal.ZERO).add(delta));
        }
      }
    }
  }

  @Override
  public UserBalanceResponseDTO getUserBalances(Long userId) {
    List<GroupMember> memberships = groupMemberRepository.findByUserId(userId);
    Map<Long, String> groupNames =
        memberships.stream()
            .collect(
                Collectors.toMap(
                    m -> m.getGroup().getId(), m -> m.getGroup().getName(), (n1, n2) -> n1));

    Set<Long> groupIds = groupNames.keySet();

    BigDecimal totalBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    List<UserBalanceResponseDTO.GroupBalanceDTO> groupBalances = new ArrayList<>();

    if (!groupIds.isEmpty()) {
      Map<Long, Map<Long, BigDecimal>> balancesByUserAndGroup =
          calculateBalancesInGroups(List.of(userId), groupIds);

      Map<Long, BigDecimal> userGroupBalances =
          balancesByUserAndGroup.getOrDefault(userId, Collections.emptyMap());

      for (Map.Entry<Long, BigDecimal> entry : userGroupBalances.entrySet()) {
        Long groupId = entry.getKey();
        BigDecimal balance = entry.getValue();
        totalBalance = totalBalance.add(balance);
        groupBalances.add(
            new UserBalanceResponseDTO.GroupBalanceDTO(
                groupId, groupNames.getOrDefault(groupId, "Unknown"), balance));
      }
    }

    BigDecimal globalSettlementBalance = calculateUserGlobalSettlementBalance(userId);
    totalBalance = totalBalance.add(globalSettlementBalance);

    UserResponse user = userClient.getUserById(userId).orElse(null);

    return UserBalanceResponseDTO.builder()
        .userId(userId)
        .username(user != null ? user.getUsername() : null)
        .email(user != null ? user.getEmail() : null)
        .totalBalance(totalBalance)
        .groupBalances(groupBalances)
        .build();
  }

  @Override
  public FriendBalanceResponseDTO getNetBalanceWithFriend(Long userId, Long friendId) {
    List<GroupMember> userMemberships = groupMemberRepository.findByUserId(userId);
    List<GroupMember> friendMemberships = groupMemberRepository.findByUserId(friendId);

    Set<Long> friendGroupIds =
        friendMemberships.stream().map(m -> m.getGroup().getId()).collect(Collectors.toSet());

    List<Long> sharedGroupIds =
        userMemberships.stream()
            .map(m -> m.getGroup().getId())
            .filter(friendGroupIds::contains)
            .distinct()
            .toList();

    BigDecimal totalNetBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    List<FriendGroupBalanceDTO> groupBalances = new ArrayList<>();

    for (Long groupId : sharedGroupIds) {
      BigDecimal groupNetBalance =
          ledgerRepository
              .calculatePairwiseBalanceInGroup(userId, friendId, groupId)
              .setScale(2, RoundingMode.HALF_UP);
      totalNetBalance = totalNetBalance.add(groupNetBalance);

      String groupName =
          userMemberships.stream()
              .filter(m -> m.getGroup().getId().equals(groupId))
              .findFirst()
              .map(m -> m.getGroup().getName())
              .orElse("Unknown Group");

      groupBalances.add(
          FriendGroupBalanceDTO.builder()
              .groupId(groupId)
              .groupName(groupName)
              .balance(groupNetBalance)
              .build());
    }

    BigDecimal globalSettlementBalance = calculateGlobalSettlementBalance(userId, friendId);
    totalNetBalance = totalNetBalance.add(globalSettlementBalance);

    return FriendBalanceResponseDTO.builder()
        .userId(userId)
        .friendId(friendId)
        .netBalance(totalNetBalance)
        .groupBalances(groupBalances)
        .build();
  }

  @Override
  public BigDecimal calculateUserBalanceInGroup(Long userId, Long groupId) {
    Map<Long, Map<Long, BigDecimal>> balances =
        calculateBalancesInGroups(List.of(userId), List.of(groupId));
    return balances
        .getOrDefault(userId, Collections.emptyMap())
        .getOrDefault(groupId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
  }

  @Override
  public Map<Long, BigDecimal> calculateGroupBalances(Long groupId, List<Long> memberIds) {
    Map<Long, BigDecimal> balances = new HashMap<>();
    if (memberIds != null) {
      for (Long memberId : memberIds) {
        balances.put(memberId, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
      }
    }
    List<UserBalanceAggregate> aggregates = ledgerRepository.calculateGroupBalances(groupId);
    if (aggregates != null) {
      for (UserBalanceAggregate agg : aggregates) {
        if (agg.getUserId() != null && agg.getBalance() != null) {
          balances.put(agg.getUserId(), agg.getBalance().setScale(2, RoundingMode.HALF_UP));
        }
      }
    }
    return balances;
  }

  @Override
  public Map<Long, Map<Long, BigDecimal>> calculateBalancesInGroups(
      Collection<Long> userIds, Collection<Long> groupIds) {
    Map<Long, Map<Long, BigDecimal>> paid =
        aggregate(expenseRepository.calculateTotalPaidByUsersInGroups(userIds, groupIds));
    Map<Long, Map<Long, BigDecimal>> share =
        aggregate(expenseRepository.calculateTotalShareForUsersInGroups(userIds, groupIds));
    Map<Long, Map<Long, BigDecimal>> settlementsPaid =
        aggregate(
            paymentRepository.calculateTotalPaymentsPaidInGroups(
                userIds, groupIds, SETTLEMENT_STATUSES));
    Map<Long, Map<Long, BigDecimal>> settlementsReceived =
        aggregate(
            paymentRepository.calculateTotalPaymentsReceivedInGroups(
                userIds, groupIds, SETTLEMENT_STATUSES));

    Map<Long, Map<Long, BigDecimal>> balances = new HashMap<>();
    for (Long userId : userIds) {
      Map<Long, BigDecimal> byGroup = new HashMap<>();
      for (Long groupId : groupIds) {
        byGroup.put(
            groupId,
            amountOrZero(paid, userId, groupId)
                .subtract(amountOrZero(share, userId, groupId))
                .add(amountOrZero(settlementsPaid, userId, groupId))
                .subtract(amountOrZero(settlementsReceived, userId, groupId))
                .setScale(2, RoundingMode.HALF_UP));
      }
      balances.put(userId, byGroup);
    }
    return balances;
  }

  private BigDecimal calculateSettledSum(Long payerId, Long payeeId) {
    return defaultZero(
            paymentRepository.calculateTotalDirectSettledBetweenUsers(
                payerId, payeeId, SettlementStatus.COMPLETED))
        .add(
            defaultZero(
                paymentRepository.calculateTotalDirectSettledBetweenUsers(
                    payerId, payeeId, SettlementStatus.MARKED_PAID)));
  }

  BigDecimal calculateGlobalSettlementBalance(Long userId, Long friendId) {
    return calculateSettledSum(userId, friendId)
        .subtract(calculateSettledSum(friendId, userId))
        .setScale(2, RoundingMode.HALF_UP);
  }

  BigDecimal calculateUserGlobalSettlementBalance(Long userId) {
    List<Payment> directPayments =
        paymentRepository.findDirectPaymentsForUser(userId, SETTLEMENT_STATUSES);
    BigDecimal totalBalance = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    if (directPayments != null) {
      for (Payment payment : directPayments) {
        if (payment.getPayerId().equals(userId)) {
          totalBalance = totalBalance.add(payment.getAmount());
        } else {
          totalBalance = totalBalance.subtract(payment.getAmount());
        }
      }
    }
    return totalBalance;
  }

  private BigDecimal defaultZero(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private BigDecimal amountOrZero(Map<Long, Map<Long, BigDecimal>> map, Long userId, Long groupId) {
    return map.getOrDefault(userId, Collections.emptyMap()).getOrDefault(groupId, BigDecimal.ZERO);
  }

  private Map<Long, Map<Long, BigDecimal>> aggregate(List<UserGroupAggregate> rows) {
    Map<Long, Map<Long, BigDecimal>> result = new HashMap<>();
    if (rows == null) {
      return result;
    }
    for (UserGroupAggregate row : rows) {
      result
          .computeIfAbsent(row.getUserId(), k -> new HashMap<>())
          .put(row.getGroupId(), row.getTotal());
    }
    return result;
  }
}
