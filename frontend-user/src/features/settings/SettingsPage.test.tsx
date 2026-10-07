import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { SettingsPage } from "./SettingsPage";
import { simplificationService } from "../balances/simplificationService";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

vi.mock("../balances/simplificationService");
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

describe("SettingsPage", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    vi.clearAllMocks();
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false, gcTime: 0, staleTime: 0 } },
    });
  });

  afterEach(() => cleanup());

  const renderPage = () =>
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <SettingsPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

  it("shows a loader while the preference loads", () => {
    (simplificationService.getAccountPreference as ReturnType<typeof vi.fn>).mockReturnValue(
      new Promise(() => {}),
    );
    renderPage();
    expect(screen.getByTestId("preference-loader")).toBeInTheDocument();
  });

  it("renders the opt-out switch off by default", async () => {
    (simplificationService.getAccountPreference as ReturnType<typeof vi.fn>).mockResolvedValue({
      userId: 1,
      accountOptOut: false,
    });
    renderPage();

    const toggle = await screen.findByLabelText("Opt out of debt netting account-wide");
    expect(toggle).toHaveAttribute("aria-checked", "false");
    expect(screen.queryByTestId("account-opt-out-notice")).not.toBeInTheDocument();
  });

  it("renders the active notice when the account is opted out", async () => {
    (simplificationService.getAccountPreference as ReturnType<typeof vi.fn>).mockResolvedValue({
      userId: 1,
      accountOptOut: true,
    });
    renderPage();

    expect(await screen.findByLabelText("Opt out of debt netting account-wide")).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByTestId("account-opt-out-notice")).toBeInTheDocument();
    expect(screen.getByText("Opt-out active on your account")).toBeInTheDocument();
  });

  it("posts an opt-out when the switch is turned on", async () => {
    (simplificationService.getAccountPreference as ReturnType<typeof vi.fn>).mockResolvedValue({
      userId: 1,
      accountOptOut: false,
    });
    (simplificationService.toggleAccountOptOut as ReturnType<typeof vi.fn>).mockResolvedValue({
      userId: 1,
      accountOptOut: true,
    });
    renderPage();

    fireEvent.click(await screen.findByLabelText("Opt out of debt netting account-wide"));

    await waitFor(() => {
      expect(simplificationService.toggleAccountOptOut).toHaveBeenCalledWith({ optOut: true });
    });
  });

  it("posts an opt-in when the switch is turned off", async () => {
    (simplificationService.getAccountPreference as ReturnType<typeof vi.fn>).mockResolvedValue({
      userId: 1,
      accountOptOut: true,
    });
    (simplificationService.toggleAccountOptOut as ReturnType<typeof vi.fn>).mockResolvedValue({
      userId: 1,
      accountOptOut: false,
    });
    renderPage();

    fireEvent.click(await screen.findByLabelText("Opt out of debt netting account-wide"));

    await waitFor(() => {
      expect(simplificationService.toggleAccountOptOut).toHaveBeenCalledWith({ optOut: false });
    });
  });
});