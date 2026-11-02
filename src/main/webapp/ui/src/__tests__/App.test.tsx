import { render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import App from "@/App";

const refreshAuthentication = vi.hoisted(() => vi.fn());
vi.mock("@/stores/use-stores", () => ({
  default: () => ({ authStore: { refreshAuthentication, isAuthenticated: false } }),
}));
vi.mock("@/Router", () => ({ default: () => null }));
vi.mock("@/components/ErrorBoundary", () => ({ ERROR_MSG: "Authentication failed" }));

it("shows an error when authentication initialization rejects", async () => {
  silenceConsole(["error"], ["Application initialization failed"]);
  const error = new Error("Token service unavailable");
  refreshAuthentication.mockRejectedValue(error);
  render(<App />);
  expect(await screen.findByText("Authentication failed")).toBeVisible();
  expect(console.error).toHaveBeenCalledWith("Application initialization failed", error);
});
