import { describe, it, expect, vi } from "vitest";
import {
  isGlobalPayment,
  recordPayment,
  confirmPayment,
  type ConfirmTransport,
} from "./settlement";
import type { Settlement } from "./settlementService";

function settlement(over: Partial<Settlement> = {}): Settlement {
  return {
    id: 1,
    payerId: 1,
    payeeId: 2,
    amount: 30,
    currency: "USD",
    groupId: 9,
    status: "PENDING",
    createdAt: "2025-01-01T10:00:00Z",
    updatedAt: "2025-01-01T10:00:00Z",
    allocations: [{ groupId: 9, amount: 30 }],
    ...over,
  };
}

describe("settlement — isGlobalPayment", () => {
  it("returns true when allocations are undefined", () => {
    expect(isGlobalPayment(settlement({ allocations: undefined }))).toBe(true);
  });

  it("returns true when allocations are empty", () => {
    expect(isGlobalPayment(settlement({ allocations: [] }))).toBe(true);
  });

  it("returns true when any allocation has a null groupId", () => {
    const s = settlement({
      allocations: [
        { groupId: 9, amount: 20 },
        { groupId: null, amount: 10 },
      ],
    });
    expect(isGlobalPayment(s)).toBe(true);
  });

  it("returns true when an allocation omits groupId", () => {
    const s = settlement({ allocations: [{ groupId: null, amount: 30 }] });
    expect(isGlobalPayment(s)).toBe(true);
  });

  it("returns false when all allocations carry a groupId", () => {
    const s = settlement({
      allocations: [
        { groupId: 9, amount: 20 },
        { groupId: 12, amount: 10 },
      ],
    });
    expect(isGlobalPayment(s)).toBe(false);
  });
});

describe("settlement — recordPayment", () => {
  it("creates then marks paid when the created settlement is PENDING", async () => {
    const createSettlement = vi.fn().mockResolvedValue(settlement({ status: "PENDING" }));
    const markAsPaid = vi.fn().mockResolvedValue(settlement({ status: "MARKED_PAID" }));
    const transport = { createSettlement, markAsPaid };

    const result = await recordPayment(transport, {
      payerId: 1,
      payeeId: 2,
      amount: 30,
      currency: "USD",
      groupId: 9,
    });

    expect(createSettlement).toHaveBeenCalledTimes(1);
    expect(markAsPaid).toHaveBeenCalledTimes(1);
    expect(markAsPaid).toHaveBeenCalledWith(1);
    expect(result.status).toBe("MARKED_PAID");
  });

  it("does not mark paid when the created settlement is already marked paid", async () => {
    const createSettlement = vi
      .fn()
      .mockResolvedValue(settlement({ status: "MARKED_PAID" }));
    const markAsPaid = vi.fn();
    const transport = { createSettlement, markAsPaid };

    const result = await recordPayment(transport, {
      payerId: 1,
      payeeId: 2,
      amount: 30,
      currency: "USD",
      groupId: 9,
    });

    expect(createSettlement).toHaveBeenCalledTimes(1);
    expect(markAsPaid).not.toHaveBeenCalled();
    expect(result.status).toBe("MARKED_PAID");
  });

  it("does not mark paid when the created settlement is already COMPLETED", async () => {
    const createSettlement = vi
      .fn()
      .mockResolvedValue(settlement({ status: "COMPLETED" }));
    const markAsPaid = vi.fn();
    const transport = { createSettlement, markAsPaid };

    const result = await recordPayment(transport, {
      payerId: 1,
      payeeId: 2,
      amount: 30,
      currency: "USD",
      groupId: 9,
    });

    expect(markAsPaid).not.toHaveBeenCalled();
    expect(result.status).toBe("COMPLETED");
  });
});

describe("settlement — confirmPayment", () => {
  it("delegates to the transport confirm and returns the confirmed settlement", async () => {
    const confirmSettlement = vi
      .fn()
      .mockResolvedValue(settlement({ status: "COMPLETED", id: 7 }));
    const transport: ConfirmTransport<Settlement> = { confirmSettlement };
    const result = await confirmPayment(transport, 7);

    expect(confirmSettlement).toHaveBeenCalledWith(7);
    expect(result.id).toBe(7);
    expect(result.status).toBe("COMPLETED");
  });

  it("works for the friendship adapter too, which returns a FriendshipSettlementDTO", async () => {
    const friendshipSettlement = {
      id: 3,
      payerId: 1,
      payeeId: 2,
      amount: 25,
      status: "COMPLETED" as const,
      createdAt: "2025-01-01T10:00:00Z",
      updatedAt: "2025-01-01T10:00:00Z",
    };
    const confirmSettlement = vi.fn().mockResolvedValue(friendshipSettlement);
    // The same confirmPayment seam serves both the group transport and the
    // friendship transport — the second adapter makes the seam real.
    const result = await confirmPayment({ confirmSettlement }, 3);

    expect(confirmSettlement).toHaveBeenCalledWith(3);
    expect(result).toEqual(friendshipSettlement);
  });
});
