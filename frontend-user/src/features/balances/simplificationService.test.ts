import { describe, it, expect, vi, beforeEach } from "vitest";
import { simplificationService } from "./simplificationService";
import type {
  DebtSimplificationPlan,
  GroupSimplificationSettings,
  UpdateSimplificationSettingsRequest,
} from "../../types/simplification";
import { expenseApi } from "../../lib/axios";

vi.mock("../../lib/axios", () => ({
  expenseApi: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  userApi: {
    get: vi.fn(),
    post: vi.fn(),
  },
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe("simplificationService", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches debt simplification plan for a group", async () => {
    const mockPlan: DebtSimplificationPlan = {
      groupId: 1,
      scope: "INTRA_GROUP",
      status: "COMPLETED",
      simplificationEnabled: true,
      originalTransactionCount: 4,
      simplifiedTransactionCount: 2,
      totalDebtVolume: 120.0,
      optedOutUserIds: [2],
      transactions: [
        {
          fromUserId: 1,
          fromUsername: "Alice",
          toUserId: 3,
          toUsername: "Charlie",
          amount: 60.0,
          status: "SUGGESTED",
        },
      ],
    };
    vi.mocked(expenseApi.get).mockResolvedValueOnce({ data: mockPlan });

    const result = await simplificationService.getSimplificationPlan(1);

    expect(expenseApi.get).toHaveBeenCalledWith("/groups/1/simplification-plan");
    expect(result).toEqual(mockPlan);
  });

  it("fetches group simplification settings", async () => {
    const mockSettings: GroupSimplificationSettings = {
      groupId: 1,
      simplificationEnabled: true,
      simplificationScope: "INTRA_GROUP",
      optOutUserIds: [2],
    };
    vi.mocked(expenseApi.get).mockResolvedValueOnce({ data: mockSettings });

    const result = await simplificationService.getSimplificationSettings(1);

    expect(expenseApi.get).toHaveBeenCalledWith("/groups/1/simplification-settings");
    expect(result).toEqual(mockSettings);
  });

  it("updates group simplification settings", async () => {
    const updateReq: UpdateSimplificationSettingsRequest = {
      simplificationEnabled: false,
      simplificationScope: "CROSS_GROUP",
    };
    const mockResponse: GroupSimplificationSettings = {
      groupId: 1,
      simplificationEnabled: false,
      simplificationScope: "CROSS_GROUP",
      optOutUserIds: [],
    };
    vi.mocked(expenseApi.put).mockResolvedValueOnce({ data: mockResponse });

    const result = await simplificationService.updateSimplificationSettings(1, updateReq);

    expect(expenseApi.put).toHaveBeenCalledWith(
      "/groups/1/simplification-settings",
      updateReq,
    );
    expect(result).toEqual(mockResponse);
  });

  it("toggles user opt-out status", async () => {
    const optOutReq = { optOut: true };
    const mockResponse: GroupSimplificationSettings = {
      groupId: 1,
      simplificationEnabled: true,
      simplificationScope: "INTRA_GROUP",
      optOutUserIds: [1],
    };
    vi.mocked(expenseApi.post).mockResolvedValueOnce({ data: mockResponse });

    const result = await simplificationService.toggleUserOptOut(1, optOutReq);

    expect(expenseApi.post).toHaveBeenCalledWith(
      "/groups/1/simplification-settings/opt-out",
      optOutReq,
    );
    expect(result).toEqual(mockResponse);
  });
});
