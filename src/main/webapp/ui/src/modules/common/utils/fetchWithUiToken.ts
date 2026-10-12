import { jwtDecode } from "jwt-decode";
import { clearStoredToken, saveStoredToken } from "./auth";

const recoveries = new Map<string, Promise<void>>();

function refreshTokenHash(token: string): unknown {
  try {
    return jwtDecode<{ refreshTokenHash?: unknown }>(token).refreshTokenHash;
  } catch {
    return undefined;
  }
}

async function recoverSession(authorization: string): Promise<void> {
  // Resource permission failures also use 401. Only recover an invalid token.
  const currentIdentity = await fetch("/api/v1/userDetails/whoami", {
    headers: { Authorization: authorization, "X-Requested-With": "XMLHttpRequest" },
  });
  if (currentIdentity.status !== 401) return;

  const tokenResponse = await fetch("/userform/ajax/inventoryOauthToken", {
    headers: { "X-Requested-With": "XMLHttpRequest" },
  });
  if (
    tokenResponse.status === 401 ||
    tokenResponse.status === 403 ||
    tokenResponse.headers.get("Content-Type")?.includes("text/html")
  ) {
    clearStoredToken();
    globalThis.location.assign("/login");
    return;
  }
  if (!tokenResponse.ok) return;
  const { data: token } = (await tokenResponse.json()) as { data?: unknown };
  if (typeof token !== "string" || !token) {
    clearStoredToken();
    globalThis.location.assign("/login");
    return;
  }

  // Check the replacement before reloading, to avoid a loop for disabled accounts.
  const response = await fetch("/api/v1/userDetails/whoami", {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
  });
  if (!response.ok) return;
  clearStoredToken();
  saveStoredToken(token);
  // The server keeps one refresh token per user, so a matching hash means the old token
  // merely expired and the page state still belongs to the current user.
  const previousHash = refreshTokenHash(authorization.slice("Bearer ".length));
  if (previousHash !== undefined && previousHash === refreshTokenHash(token)) return;
  // A session identity change invalidates every query cache and the surrounding UI.
  // Reload instead of replaying a pending mutation as the new user.
  globalThis.location.reload();
}

/** Share recovery only between requests using the same rejected UI token. */
export function recoverUiToken(authorization: string): Promise<void> {
  let recovery = recoveries.get(authorization);
  if (!recovery) {
    recovery = recoverSession(authorization)
      .catch(() => {
        // Preserve the original 401 for the caller's error handling if recovery fails.
        console.warn("Unable to refresh the browser session token.");
      })
      .finally(() => {
        recoveries.delete(authorization);
      });
    recoveries.set(authorization, recovery);
  }
  return recovery;
}

/** Native-fetch UI API requests must recover from tokens rejected after Operate As. */
export async function fetchWithUiToken(url: string, init: RequestInit): Promise<Response> {
  const response = await fetch(url, init);
  const authorization = new Headers(init.headers).get("Authorization");
  if (response.status === 401 && authorization?.startsWith("Bearer ")) {
    await recoverUiToken(authorization);
  }
  return response;
}
