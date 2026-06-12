import { vi } from "vitest";

/*
 * `getToken` MUST be a stable module-level reference, NOT a fresh closure on
 * every `default()` call. The real hook returns a `getToken` memoised with
 * `useCallback([])`; tests that consumed an unstable mock triggered infinite
 * render loops in components/hooks that `useCallback`-memoise on `[getToken]`
 * (notably `useFolders`'s `getFolder` / `getFolderTree`), surfacing as
 * "Maximum update depth exceeded" in FolderTree-based flows.
 */
const stableGetToken = (): Promise<string> => Promise.resolve("token");

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: stableGetToken,
  }),
}));
