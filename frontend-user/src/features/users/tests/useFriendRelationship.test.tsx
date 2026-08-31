import { renderHook, act } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import { useFriendRelationship } from "../useFriendRelationship";
import type { InterpersonalResult } from "../interpersonal";

describe("useFriendRelationship", () => {
  it("handles friend request modals and trigger mutations", () => {
    const mockSend = vi.fn();
    const mockCancel = vi.fn();

    const mockInterpersonal = {
      mutations: {
        sendFriendRequest: { mutate: mockSend },
        cancelFriendRequest: { mutate: mockCancel },
      },
    } as unknown as InterpersonalResult;

    const { result } = renderHook(() =>
      useFriendRelationship(mockInterpersonal),
    );

    act(() => {
      result.current.setIsAddFriendModalOpen(true);
    });
    expect(result.current.isAddFriendModalOpen).toBe(true);

    act(() => {
      result.current.handleAddFriend();
    });
    expect(mockSend).toHaveBeenCalled();

    act(() => {
      result.current.setIsCancelRequestModalOpen(true);
    });
    expect(result.current.isCancelRequestModalOpen).toBe(true);

    act(() => {
      result.current.handleCancelRequest();
    });
    expect(mockCancel).toHaveBeenCalled();
  });
});
