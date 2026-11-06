import { render, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import Router from "@/Router";

const authenticate = vi.hoisted(() => vi.fn());
vi.mock("@/stores/use-stores", () => ({
  default: () => ({ authStore: { authenticate, isSynchronizing: false, isAuthenticated: false } }),
}));
vi.mock("@/components/Confirm", () => ({ default: () => null }));

it("handles authentication rejection without an unhandled promise", async () => {
  silenceConsole(["error"], ["Router authentication failed"]);
  silenceConsole(["warn"], ["No routes matched location"]);
  const error = new Error("No usable token");
  authenticate.mockRejectedValue(error);
  render(<Router />);
  await waitFor(() => expect(console.error).toHaveBeenCalledWith("Router authentication failed", error));
});
