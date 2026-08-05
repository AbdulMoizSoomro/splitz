import { useMemo } from "react";
import { useLedger } from "./useLedger";
import { MONEY_TOLERANCE, type Counterparty } from "../features/balances/ledger";
import { deriveRelationshipStatus } from "../features/users/interpersonal";

export interface TempFriend {
  userId: number;
  username: string;
  firstName: string;
  lastName: string;
  balance: number;
  groups: { id: number; name: string }[];
}

/**
 * People the current user has a non-zero money position with, outside the
 * friend list. The ledger owns the aggregation and sign conventions; this
 * hook only filters its counterparties down to "not a friend, not settled".
 */
export const useTempFriends = () => {
  const { ledger, friends, isLoading } = useLedger({ detail: true });

  const tempFriends: TempFriend[] = useMemo(() => {
    return (ledger?.counterparties ?? [])
      .filter(
        (cp: Counterparty) =>
          cp.userId !== ledger?.key &&
          deriveRelationshipStatus({
            targetUserId: cp.userId,
            friends,
            netBalance: cp.balance,
          }) === "TEMP_FRIEND",
      )
      .map((cp) => {
        const parts = cp.name.split(" ");
        return {
          userId: cp.userId,
          username: cp.name,
          firstName: parts[0],
          lastName: parts.slice(1).join(" "),
          balance: cp.balance,
          groups: cp.groups,
        };
      });
  }, [ledger, friends]);

  return {
    tempFriends,
    isLoading,
  };
};
