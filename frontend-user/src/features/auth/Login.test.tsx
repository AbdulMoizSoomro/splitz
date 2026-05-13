import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import Login from "./Login";
import api from "../../lib/axios";
import { BrowserRouter, useNavigate } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";

vi.mock("../../lib/axios");
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: vi.fn(),
  };
});

describe("Login", () => {
  const mockNavigate = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(useNavigate).mockReturnValue(mockNavigate);
    useAuthStore.getState().logout();
  });

  it("renders login form", () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    );
    expect(screen.getByLabelText(/username/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i, { selector: "input" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /login/i })).toBeInTheDocument();
  });

  it("handles successful login", async () => {
    const mockAuthResponse = {
      data: {
        token: "fake-jwt-token",
      },
    };
    const mockUserResponse = {
      data: { id: "1", username: "testuser", email: "test@example.com" },
    };
    vi.mocked(api.post).mockResolvedValueOnce(mockAuthResponse);
    vi.mocked(api.get).mockResolvedValueOnce(mockUserResponse);

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    );

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: { value: "testuser" },
    });
    fireEvent.change(screen.getByLabelText(/password/i, { selector: "input" }), {
      target: { value: "password123" },
    });
    fireEvent.click(screen.getByRole("button", { name: /login/i }));

    await waitFor(() => {
      expect(api.post).toHaveBeenCalledWith("/authenticate", {
        username: "testuser",
        password: "password123",
      });
      expect(api.get).toHaveBeenCalledWith("/users/me");
      expect(useAuthStore.getState().token).toBe("fake-jwt-token");
      expect(useAuthStore.getState().user?.username).toBe("testuser");
      expect(mockNavigate).toHaveBeenCalledWith("/");
    });
  });

  it("displays error message on failed login", async () => {
    vi.mocked(api.post).mockRejectedValueOnce({
      response: { data: { message: "Invalid credentials" } },
    });

    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    );

    fireEvent.change(screen.getByLabelText(/username/i), {
      target: { value: "wronguser" },
    });
    fireEvent.change(screen.getByLabelText(/password/i, { selector: "input" }), {
      target: { value: "wrongpass" },
    });
    fireEvent.click(screen.getByRole("button", { name: /login/i }));

    await waitFor(() => {
      expect(screen.getByText(/failed to login/i)).toBeInTheDocument();
    });
  });

  it("shows validation errors for empty fields", async () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    );

    fireEvent.click(screen.getByRole("button", { name: /login/i }));

    expect(screen.getByText(/username is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();
    expect(api.post).not.toHaveBeenCalled();
  });

  it("clears validation errors on input change", async () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    );

    fireEvent.click(screen.getByRole("button", { name: /login/i }));

    const usernameInput = screen.getByLabelText(/username/i);
    const passwordInput = screen.getByLabelText(/password/i, { selector: "input" });

    expect(screen.getByText(/username is required/i)).toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();

    fireEvent.change(usernameInput, { target: { value: "u" } });
    expect(screen.queryByText(/username is required/i)).not.toBeInTheDocument();
    expect(screen.getByText(/password is required/i)).toBeInTheDocument();

    fireEvent.change(passwordInput, { target: { value: "p" } });
    expect(screen.queryByText(/password is required/i)).not.toBeInTheDocument();
  });

  it("toggles password visibility when clicking the eye icon", () => {
    render(
      <BrowserRouter>
        <Login />
      </BrowserRouter>,
    );

    const passwordInput = screen.getByLabelText(/password/i, { selector: "input" }) as HTMLInputElement;
    expect(passwordInput.type).toBe("password");

    const toggleButton = screen.getByRole("button", {
      name: /toggle visibility/i,
    });
    
    // Initial state: password hidden, should show Eye icon
    expect(toggleButton.querySelector("svg")).toHaveAttribute("data-lucide", "eye");

    fireEvent.click(toggleButton);
    expect(passwordInput.type).toBe("text");
    expect(toggleButton.querySelector("svg")).toHaveAttribute("data-lucide", "eye-off");

    fireEvent.click(toggleButton);
    expect(passwordInput.type).toBe("password");
    expect(toggleButton.querySelector("svg")).toHaveAttribute("data-lucide", "eye");
  });
});
