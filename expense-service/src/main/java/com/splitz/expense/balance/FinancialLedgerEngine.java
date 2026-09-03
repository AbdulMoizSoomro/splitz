package com.splitz.expense.balance;

import com.splitz.expense.dto.CounterpartyResponseDTO;
import com.splitz.expense.dto.FriendBalanceResponseDTO;
import com.splitz.expense.dto.GroupBalanceResponseDTO;
import com.splitz.expense.dto.UserBalanceResponseDTO;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Deep Financial Balance & Ledger Module: consolidates balance calculation, multi-group SQL
 * aggregations, and counterparty resolution behind a single seam.
 */
public interface FinancialLedgerEngine {

  /**
   * Resolves all counterparties across the user's groups with non-zero balances, enriched with user
   * profiles from UserClient and shared group references.
   */
  List<CounterpartyResponseDTO> getCounterparties(Long userId);

  /**
   * Resolves the user's total net balance, group-by-group balance breakdown, and global friendship
   * settlements.
   */
  UserBalanceResponseDTO getUserBalances(Long userId);

  /** Resolves net balance with a friend across all shared groups and global settlements. */
  FriendBalanceResponseDTO getNetBalanceWithFriend(Long userId, Long friendId);

  /** Resolves member balance sheets and simplified group debts. */
  GroupBalanceResponseDTO getGroupBalances(Long groupId);

  /** Calculates a user's net balance within a specific group using financial aggregates. */
  BigDecimal calculateUserBalanceInGroup(Long userId, Long groupId);

  /** Batch calculates net balances of members in groups. */
  Map<Long, Map<Long, BigDecimal>> calculateBalancesInGroups(
      Collection<Long> userIds, Collection<Long> groupIds);

  /** Resolves group balances for given memberIds in a group. */
  Map<Long, BigDecimal> calculateGroupBalances(Long groupId, List<Long> memberIds);
}
