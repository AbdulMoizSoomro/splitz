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
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
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
    fireEvent.change(screen.getByLabelText(/password/i), {
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
    fireEvent.change(screen.getByLabelText(/password/i), {
      target: { value: "wrongpass" },
    });
    fireEvent.click(screen.getByRole("button", { name: /login/i }));

    await waitFor(() => {
      expect(screen.getByText(/failed to login/i)).toBeInTheDocument();
    });
  });
});
