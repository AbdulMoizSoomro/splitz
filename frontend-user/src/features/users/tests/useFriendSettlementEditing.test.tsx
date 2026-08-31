import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { useFriendSettlementEditing } from "../useFriendSettlementEditing";
import type { InterpersonalResult } from "../interpersonal";
import type { FriendshipSettlementDTO } from "../../../types/user";

describe("useFriendSettlementEditing", () => {
  it("handles settlement editing, validation, and submission", () => {
    const mockConfirm = vi.fn();
    const mockUpdate = vi.fn();

    const mockInterpersonal = {
      mutations: {
        confirmSettlement: { mutate: mockConfirm },
        updateSettlement: { mutate: mockUpdate },
      },
    } as unknown as InterpersonalResult;

    const { result } = renderHook(() =>
      useFriendSettlementEditing(mockInterpersonal),
    );

    const mockSettlement: FriendshipSettlementDTO = {
      id: 10,
      payerId: 1,
      payeeId: 2,
      amount: 45.5,
      settlementDate: "2025-01-01T10:00:00Z",
      status: "COMPLETED",
    };

    act(() => {
      result.current.startEditing(mockSettlement);
    });
    expect(result.current.editingSettlementId).toBe(10);
    expect(result.current.editAmount).toBe("45.50");

    act(() => {
      result.current.setEditAmount("50.00");
    });
    expect(result.current.editAmount).toBe("50.00");

    act(() => {
      result.current.submitEdit(10);
    });
    expect(mockUpdate).toHaveBeenCalledWith(
      { settlementId: 10, amount: 50 },
      expect.any(Object),
    );

    act(() => {
      result.current.cancelEditing();
    });
    expect(result.current.editingSettlementId).toBeNull();
    expect(result.current.editAmount).toBe("");
  });
});
