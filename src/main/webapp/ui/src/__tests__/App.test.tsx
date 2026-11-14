import "@/__tests__/__mocks__/matchMedia";
import "@/stores/stores/RootStore";
import { act, render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { afterEach, expect, it, onTestFinished, vi } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import App from "@/App";
import JwtService from "@/common/JwtService";
import { createUiOAuthToken } from "@/modules/common/api/v2/generated/oauth/oauth";
import { setApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { resetUiTokenForTests } from "@/modules/common/utils/auth";
import AuthStore from "@/stores/stores/AuthStore";
import type { RootStore } from "@/stores/stores/RootStore";

const stores = vi.hoisted(() => ({ current: undefined as unknown as RootStore }));
vi.mock("@/stores/use-stores", () => ({ default: () => stores.current }));
vi.mock("@/Router", () => ({ default: () => <h1>{"Inventory ready"}</h1> }));
vi.mock("@/components/GoogleLoginProvider", () => ({ default: () => null }));
vi.mock("@/components/Analytics", () => ({ default: ({ children }: { children: React.ReactNode }) => children }));
vi.mock("@/components/ErrorBoundary", () => ({ ERROR_MSG: "Authentication failed" }));

afterEach(() => {
  act(() => stores.current.authStore.signOut?.());
  vi.useRealTimers();
  sessionStorage.clear();
});

it("shows an error when authentication initialization rejects", async () => {
  silenceConsole(["error"], ["Application initialization failed"]);
  const error = new Error("Token service unavailable");
  stores.current = {
    authStore: { refreshAuthentication: vi.fn().mockRejectedValue(error), isAuthenticated: false },
  } as unknown as RootStore;
  render(<App />);
  expect(await screen.findByText("Authentication failed")).toBeVisible();
  expect(console.error).toHaveBeenCalledWith("Application initialization failed", error);
});

it("loads the current user with an expiring token after a transient mint failure and recovers", async () => {
  onTestFinished(
    setApiV2TokenProvider({
      getToken: async () => (await createUiOAuthToken()).accessToken,
      recover: async () => (await createUiOAuthToken()).accessToken,
    }),
  );
  resetUiTokenForTests();
  vi.useFakeTimers();
  const token = (seconds: number) =>
    `eyJhbGciOiJub25lIn0.${btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds }))}.signature`;
  const stored = token(20);
  const fresh = token(3600);
  JwtService.saveToken(stored);
  const fetchCurrentUser = vi.fn().mockResolvedValue({ getBench: vi.fn().mockResolvedValue(undefined) });
  stores.current = {
    peopleStore: { fetchCurrentUser, currentUser: {} },
    unitStore: { fetchUnits: vi.fn().mockResolvedValue(undefined) },
  } as unknown as RootStore;
  const authStore = new AuthStore(stores.current);
  stores.current.authStore = authStore;
  vi.spyOn(authStore, "fetchPidinstEnabled").mockResolvedValue();
  const authenticate = vi.spyOn(authStore, "authenticate");
  let requests = 0;
  server.use(
    http.post("*/api/v2/oauth/tokens", () => {
      requests++;
      return requests === 1 ? new HttpResponse(null, { status: 503 }) : HttpResponse.json({ accessToken: fresh });
    }),
  );
  render(<App />);
  await act(async () => {
    await authenticate.mock.results[0].value;
  });
  expect(fetchCurrentUser).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("heading", { name: "Inventory ready" })).toBeInTheDocument();
  expect(JwtService.getToken()).toBe(stored);
  await act(async () => {
    await vi.advanceTimersByTimeAsync(1000);
    await authenticate.mock.results.at(-1)?.value;
  });
  expect(JwtService.getToken()).toBe(fresh);
  expect(authStore.isAuthenticated).toBe(true);
  expect(fetchCurrentUser).toHaveBeenCalledTimes(1);
});
