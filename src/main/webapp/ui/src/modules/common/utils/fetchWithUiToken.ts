import { jwtDecode } from "jwt-decode";
import { getApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";
import { getStoredToken, refreshTokenHash, saveStoredToken } from "./auth";

const recoveries = new Map<string, Promise<string | undefined>>();

export function isUiApiUrl(url: string): boolean {
  // A timer can still send a request after a test's page has been torn down.
  if (!globalThis.location) return false;
  const target = new URL(url, globalThis.location.href);
  return target.origin === globalThis.location.origin && target.pathname.startsWith("/api/");
}

/** Use a refreshed token even when a hook or query still holds the expired one. */
export function getUiAuthorization(authorization: string): string {
  const storedToken = getStoredToken();
  const hash = refreshTokenHash(authorization.slice("Bearer ".length));
  if (storedToken && hash !== undefined && hash === refreshTokenHash(storedToken)) {
    const storedIssuedAt = jwtDecode<{ iat?: number }>(storedToken).iat;
    const issuedAt = jwtDecode<{ iat?: number }>(authorization.slice("Bearer ".length)).iat;
    if (typeof storedIssuedAt === "number" && typeof issuedAt === "number" && storedIssuedAt > issuedAt) {
      return `Bearer ${storedToken}`;
    }
  }
  return authorization;
}

async function recoverSession(authorization: string, identityRejected: boolean): Promise<string | undefined> {
  const rejected = authorization.slice("Bearer ".length);
  const token = await getApiV2TokenProvider().recover(rejected, identityRejected);
  if (token === rejected) return;
  // Validate the replacement to avoid a reload loop for disabled accounts.
  const response = await fetch("/api/v1/userDetails/whoami", {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
  });
  if (!response.ok) return;
  saveStoredToken(token);
  // The server keeps one refresh token per user, so a matching hash means the old token
  // merely expired and requests can safely keep using the page's identity.
  const previousHash = refreshTokenHash(rejected);
  if (previousHash !== undefined && previousHash === refreshTokenHash(token)) return token;
  // Reload to discard caches and page state belonging to the previous identity.
  globalThis.location.reload();
}

/** Share recovery only between requests using the same rejected UI token. */
export function recoverUiToken(authorization: string, identityRejected = false): Promise<string | undefined> {
  let recovery = recoveries.get(authorization);
  if (!recovery) {
    recovery = recoverSession(authorization, identityRejected)
      .catch(() => {
        // Preserve the original 401 for the caller's error handling if recovery fails.
        console.warn("Unable to refresh the browser session token.");
        return undefined;
      })
      .finally(() => {
        recoveries.delete(authorization);
      });
    recoveries.set(authorization, recovery);
  }
  return recovery;
}

export async function fetchWithUiToken(url: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers);
  let authorization = headers.get("Authorization");
  const uiRequest = isUiApiUrl(url) && authorization?.startsWith("Bearer ");
  if (uiRequest && authorization) {
    authorization = getUiAuthorization(authorization);
    headers.set("Authorization", authorization);
  }
  const response = await fetch(url, { ...init, headers });
  if (response.status === 401 && uiRequest && authorization) {
    const identityRejected =
      new URL(url, globalThis.location.href).pathname.replace(/\/$/, "") === "/api/v1/userDetails/whoami";
    const token = await recoverUiToken(authorization, identityRejected);
    if (token && (init.method ?? "GET").toUpperCase() === "GET") {
      headers.set("Authorization", `Bearer ${token}`);
      return fetch(url, { ...init, headers });
    }
  }
  return response;
}
