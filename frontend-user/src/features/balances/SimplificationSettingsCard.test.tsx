import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { SimplificationSettingsCard } from "./SimplificationSettingsCard";
import { simplificationService } from "./simplificationService";
import type { GroupSimplificationSettings } from "../../types/simplification";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("./simplificationService");

describe("SimplificationSettingsCard", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false, gcTime: 0, staleTime: 0 },
      },
    });
  });

  afterEach(() => {
    cleanup();
  });

  const renderComponent = (props: { groupId: number; isAdminOrOwner: boolean; currentUserId: number }) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <SimplificationSettingsCard {...props} />
      </QueryClientProvider>,
    );
  };

  it("renders settings card with admin governance controls for admin user", async () => {
    const mockSettings: GroupSimplificationSettings = {
      groupId: 1,
      simplificationEnabled: true,
      simplificationScope: "INTRA_GROUP",
      optOutUserIds: [],
    };

    vi.mocked(simplificationService.getSimplificationSettings).mockResolvedValueOnce(mockSettings);

    renderComponent({ groupId: 1, isAdminOrOwner: true, currentUserId: 1 });

    expect(await screen.findByText("Debt Simplification & Governance")).toBeInTheDocument();
    expect(screen.getByText("Enable Smart Debt Reduction")).toBeInTheDocument();
    expect(screen.getByLabelText("Select Intra-Group Scope")).toBeInTheDocument();
    expect(screen.getByLabelText("Select Global Cross-Group Scope")).toBeInTheDocument();
  });

  it("hides admin governance controls for regular member user", async () => {
    const mockSettings: GroupSimplificationSettings = {
      groupId: 1,
      simplificationEnabled: true,
      simplificationScope: "INTRA_GROUP",
      optOutUserIds: [],
    };

    vi.mocked(simplificationService.getSimplificationSettings).mockResolvedValueOnce(mockSettings);

    renderComponent({ groupId: 1, isAdminOrOwner: false, currentUserId: 2 });

    expect(await screen.findByText("Debt Simplification & Governance")).toBeInTheDocument();
    expect(screen.queryByText("Enable Smart Debt Reduction")).not.toBeInTheDocument();
    expect(screen.getByText("Opt Out of Debt Netting")).toBeInTheDocument();
  });

  it("calls updateSimplificationSettings when admin toggles governance switch", async () => {
    const mockSettings: GroupSimplificationSettings = {
      groupId: 1,
      simplificationEnabled: true,
      simplificationScope: "INTRA_GROUP",
      optOutUserIds: [],
    };

    vi.mocked(simplificationService.getSimplificationSettings).mockResolvedValueOnce(mockSettings);
    vi.mocked(simplificationService.updateSimplificationSettings).mockResolvedValueOnce({
      ...mockSettings,
      simplificationEnabled: false,
    });

    renderComponent({ groupId: 1, isAdminOrOwner: true, currentUserId: 1 });

    const toggle = await screen.findByLabelText("Enable Debt Simplification");
    fireEvent.click(toggle);

    await waitFor(() => {
      expect(simplificationService.updateSimplificationSettings).toHaveBeenCalledWith(1, {
        simplificationEnabled: false,
        simplificationScope: "INTRA_GROUP",
      });
    });
  });

  it("calls toggleUserOptOut when user toggles opt-out switch", async () => {
    const mockSettings: GroupSimplificationSettings = {
      groupId: 1,
      simplificationEnabled: true,
      simplificationScope: "INTRA_GROUP",
      optOutUserIds: [],
    };

    vi.mocked(simplificationService.getSimplificationSettings).mockResolvedValueOnce(mockSettings);
    vi.mocked(simplificationService.toggleUserOptOut).mockResolvedValueOnce({
      ...mockSettings,
      optOutUserIds: [1],
    });

    renderComponent({ groupId: 1, isAdminOrOwner: false, currentUserId: 1 });

    const optOutToggle = await screen.findByLabelText("Opt out of debt simplification");
    fireEvent.click(optOutToggle);

    await waitFor(() => {
      expect(simplificationService.toggleUserOptOut).toHaveBeenCalledWith(1, {
        optOut: true,
      });
    });
  });
});
