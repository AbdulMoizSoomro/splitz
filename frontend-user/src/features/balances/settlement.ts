import type { Settlement, CreateSettlementRequest } from "./settlementService";

/**
 * The Settlement lifecycle, owned by one module instead of being re-implemented
 * inside each component.
 *
 * The module is "deep": a small interface (isGlobalPayment, recordPayment,
 * confirmPayment) hides the orchestration of a Payment's travel from PENDING to
 * MARKED_PAID to COMPLETED. The existing services (settlementService,
 * friendService) plug in as transport adapters — the second adapter is what
 * turns the seam into a real one.
 */

/** An allocation carrying an optional group; a null/absent group means global. */
export interface SettlementAllocationLike {
  groupId: number | null;
  amount: number;
}

export interface HasAllocations {
  allocations?: SettlementAllocationLike[];
}

/** True when a Payment has no group allocation — it goes to the direct/global balance. */
export function isGlobalPayment(s: HasAllocations): boolean {
  return (
    !s.allocations ||
    s.allocations.length === 0 ||
    s.allocations.some((a) => !a.groupId)
  );
}

/** The low-level operations a Payment lifecycle needs, injected by callers. */
export interface RecordPaymentTransport {
  createSettlement(data: CreateSettlementRequest): Promise<Settlement>;
  markAsPaid(id: number): Promise<Settlement>;
}

/**
 * Record a payment: create it, and when the backend starts it as PENDING,
 * immediately advance it to MARKED_PAID. Collapses the two-step create →
 * markAsPaid hand-roll that used to live in GroupBalances.
 */
export async function recordPayment(
  transport: RecordPaymentTransport,
  data: CreateSettlementRequest,
): Promise<Settlement> {
  const created = await transport.createSettlement(data);
  if (created.status === "PENDING") {
    return transport.markAsPaid(created.id);
  }
  return created;
}

/** The single operation a confirm needs, shared by both transports. */
export interface ConfirmTransport<T> {
  confirmSettlement(id: number): Promise<T>;
}

/** Confirm a marked-paid Payment, completing its lifecycle. */
export function confirmPayment<T>(
  transport: ConfirmTransport<T>,
  id: number,
): Promise<T> {
  return transport.confirmSettlement(id);
}
