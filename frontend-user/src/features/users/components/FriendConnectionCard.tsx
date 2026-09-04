import React from "react";
import { Link } from "react-router-dom";
import {
  Users,
  UserPlus,
  UserMinus,
  Clock,
  MoreVertical,
  ArrowUpRight,
  ArrowDownLeft,
  ExternalLink,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
} from "@/components/ui/dropdown-menu";
import type { UnifiedConnection } from "../friendsLedger";
import { MONEY_TOLERANCE } from "../../balances/ledger";

interface FriendConnectionCardProps {
  connection: UnifiedConnection;
  onSettleUp: (conn: UnifiedConnection) => void;
  onAddFriend: (conn: UnifiedConnection) => void;
  onRemoveFriend: (conn: UnifiedConnection) => void;
  onCancelRequest?: (conn: UnifiedConnection) => void;
  isAddFriendPending?: boolean;
  isRemoveFriendPending?: boolean;
}

export const FriendConnectionCard: React.FC<FriendConnectionCardProps> = ({
  connection,
  onSettleUp,
  onAddFriend,
  onRemoveFriend,
  onCancelRequest,
  isAddFriendPending = false,
  isRemoveFriendPending = false,
}) => {
  const {
    userId,
    name,
    username,
    firstName,
    lastName,
    isFriend,
    isPendingOutgoing,
    balance,
    groups,
  } = connection;

  const hasBalance = Math.abs(balance) > MONEY_TOLERANCE;
  const isOwed = balance > MONEY_TOLERANCE;
  const youOwe = balance < -MONEY_TOLERANCE;

  const initials = (firstName[0] || name[0] || "?") + (lastName ? lastName[0] : "");

  const topGroups = groups.slice(0, 2);
  const remainingGroupsCount = Math.max(0, groups.length - 2);
  const remainingGroupNames = groups.slice(2).map((g) => g.name).join(", ");

  const cardClasses = !isFriend
    ? "p-4 rounded-xl border border-orange-200 dark:border-orange-900/50 bg-orange-50/30 dark:bg-orange-900/10 shadow-xs hover:shadow-sm transition-all"
    : "p-4 rounded-xl border border-border bg-card hover:border-border/80 shadow-xs hover:shadow-sm transition-all";

  return (
    <div className={cardClasses}>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        {/* Left: Avatar & Info */}
        <div className="flex items-start sm:items-center gap-3.5 min-w-0 flex-1">
          <Link
            to={`/friends/${userId}`}
            className="relative flex-shrink-0 group"
          >
            <div
              className={`w-12 h-12 rounded-full flex items-center justify-center font-bold text-base transition-transform group-hover:scale-105 ${
                !isFriend
                  ? "bg-orange-100 text-orange-800 dark:bg-orange-900/50 dark:text-orange-300"
                  : "bg-blue-100 text-blue-700 dark:bg-blue-900/50 dark:text-blue-300"
              }`}
            >
              {initials.toUpperCase()}
            </div>
            {!isFriend && (
              <div
                title="Temporary Friends"
                className="absolute -bottom-1 -right-1 bg-orange-500 text-white rounded-full p-0.5"
              >
                <Users size={12} />
              </div>
            )}
          </Link>

          <div className="min-w-0 flex-1 space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <Link
                to={`/friends/${userId}`}
                className="font-semibold text-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors truncate text-base"
              >
                {name}
              </Link>

              {!isFriend && (
                <Badge
                  variant="outline"
                  className="text-[11px] py-0 px-2 font-medium bg-orange-100 dark:bg-orange-900/30 text-orange-700 dark:text-orange-400 border-orange-200 dark:border-orange-800/50"
                >
                  Temporary Friends
                </Badge>
              )}
            </div>

            <p className="text-xs text-muted-foreground truncate">
              @{username}
            </p>

            {/* Net Balance indicator */}
            <div className="flex items-center gap-1.5 text-sm pt-0.5">
              {isOwed && (
                <span className="flex items-center gap-1 font-semibold text-emerald-600 dark:text-emerald-400">
                  <ArrowDownLeft size={15} />
                  Owes you ${balance.toFixed(2)}
                </span>
              )}
              {youOwe && (
                <span className="flex items-center gap-1 font-semibold text-rose-600 dark:text-rose-400">
                  <ArrowUpRight size={15} />
                  You owe ${Math.abs(balance).toFixed(2)}
                </span>
              )}
              {!hasBalance && (
                <span className="text-muted-foreground text-xs font-medium">
                  Settled up
                </span>
              )}
            </div>

            {/* Shared Groups Breakdown Badges */}
            {groups.length > 0 && (
              <div className="flex items-center gap-1.5 flex-wrap pt-1">
                {topGroups.map((g) => (
                  <Badge
                    key={g.id}
                    variant="secondary"
                    className="text-[11px] py-0 px-1.5 font-normal text-muted-foreground bg-muted/70 hover:bg-muted"
                  >
                    {g.name}
                  </Badge>
                ))}
                {remainingGroupsCount > 0 && (
                  <Badge
                    variant="outline"
                    title={`Also in: ${remainingGroupNames}`}
                    className="text-[11px] py-0 px-1.5 cursor-help text-muted-foreground"
                  >
                    +{remainingGroupsCount} more
                  </Badge>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center justify-end gap-2 flex-shrink-0 pt-2 sm:pt-0 border-t sm:border-t-0 border-border/50">
          {/* Settle Up Action */}
          {hasBalance && (
            <Button
              size="sm"
              onClick={() => onSettleUp(connection)}
              className="bg-emerald-600 hover:bg-emerald-700 text-white font-medium shadow-xs"
            >
              Settle Up
            </Button>
          )}

          {/* Non-friend add/cancel friend CTA */}
          {!isFriend && (
            <>
              {isPendingOutgoing ? (
                <Button
                  size="sm"
                  variant="outline"
                  className="text-rose-600 dark:text-rose-400 hover:bg-rose-50 dark:hover:bg-rose-950/20 flex items-center gap-1 text-xs"
                  onClick={() => onCancelRequest?.(connection)}
                  disabled={isAddFriendPending}
                >
                  <Clock size={14} />
                  <span>Cancel Request</span>
                </Button>
              ) : (
                <Button
                  size="sm"
                  variant="outline"
                  className="border-orange-200 dark:border-orange-800/50 hover:bg-orange-50 dark:hover:bg-orange-900/20 text-foreground flex items-center gap-1.5 text-xs"
                  onClick={() => onAddFriend(connection)}
                  disabled={isAddFriendPending}
                >
                  <UserPlus size={14} className="text-orange-600 dark:text-orange-400" />
                  <span>Add Friend</span>
                </Button>
              )}
            </>
          )}

          {/* Confirmed friend: Quick remove button with title + 3-dot dropdown */}
          {isFriend && (
            <div className="flex items-center gap-1">
              <Button
                size="sm"
                variant="outline"
                className="text-muted-foreground hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 h-8 w-8 p-0"
                onClick={() => onRemoveFriend(connection)}
                disabled={isRemoveFriendPending}
                title="Remove Friend"
                aria-label="Remove Friend"
              >
                <UserMinus size={15} />
              </Button>

              <DropdownMenu>
                <DropdownMenuTrigger
                  className="h-8 w-8 inline-flex items-center justify-center rounded-md text-muted-foreground hover:text-foreground hover:bg-muted/80 transition-colors"
                  aria-label="More options"
                >
                  <MoreVertical size={16} />
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end" className="w-40">
                  <DropdownMenuItem
                    onClick={() => {
                      window.location.href = `/friends/${userId}`;
                    }}
                    className="flex items-center gap-2 cursor-pointer text-xs"
                  >
                    <ExternalLink size={14} />
                    <span>View Details</span>
                  </DropdownMenuItem>
                  <DropdownMenuItem
                    onClick={() => onRemoveFriend(connection)}
                    disabled={isRemoveFriendPending}
                    variant="destructive"
                    className="flex items-center gap-2 cursor-pointer text-xs"
                  >
                    <UserMinus size={14} />
                    <span>Remove Friend</span>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
