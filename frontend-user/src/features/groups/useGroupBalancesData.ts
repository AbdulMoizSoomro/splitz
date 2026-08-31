import { useQuery } from "@tanstack/react-query";
import { groupService } from "./groupService";
import { settlementService } from "../balances/settlementService";
import { queryKeys } from "../../lib/queryKeys";
import { balanceInGroup } from "../balances/ledger";
import { useGroupGovernance } from "./membershipGating";
import type { Group } from "../../types/group";

export function useGroupBalancesData(
  groupId: number,
  currentUserId?: number,
  group?: Group,
) {
  const { data: balancesResponse, isLoading: isBalancesLoading } = useQuery({
    queryKey: queryKeys.groupBalances(groupId),
    queryFn: () => groupService.getBalances(groupId),
    enabled: !!groupId,
  });

  const { data: settlements, isLoading: isSettlementsLoading } = useQuery({
    queryKey: queryKeys.groupSettlements(groupId),
    queryFn: () => settlementService.getSettlementsByGroup(groupId),
    enabled: !!groupId,
  });

  const currentUserBalance = balanceInGroup(
    balancesResponse?.balances ?? [],
    Number(currentUserId),
  );

  const governance = useGroupGovernance({
    group,
    currentUserId: Number(currentUserId),
    currentUserBalance,
    settlements,
  });

  return {
    balancesResponse,
    isBalancesLoading,
    settlements,
    isSettlementsLoading,
    currentUserBalance,
    governance,
  };
}
