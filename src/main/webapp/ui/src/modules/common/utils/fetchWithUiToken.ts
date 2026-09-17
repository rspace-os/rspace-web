import { clearStoredToken, saveStoredToken } from "./auth";

let recovery: Promise<void> | undefined;

async function recoverSession(authorization: string): Promise<void> {
  // Resource permission failures also use 401. Only recover an invalid token.
  const currentIdentity = await fetch("/api/v1/userDetails/whoami", {
    headers: { Authorization: authorization, "X-Requested-With": "XMLHttpRequest" },
  });
  if (currentIdentity.status !== 401) return;

  clearStoredToken();
  const tokenResponse = await fetch("/userform/ajax/inventoryOauthToken", {
    headers: { "X-Requested-With": "XMLHttpRequest" },
  });
  if (!tokenResponse.ok) return;
  const { data: token } = (await tokenResponse.json()) as { data?: unknown };
  if (typeof token !== "string" || !token) return;

  // Check the replacement before reloading, to avoid a loop for disabled accounts.
  const response = await fetch("/api/v1/userDetails/whoami", {
    headers: { Authorization: `Bearer ${token}`, "X-Requested-With": "XMLHttpRequest" },
  });
  if (!response.ok) return;
  saveStoredToken(token);
  // A session identity change invalidates every query cache and the surrounding UI.
  // Reload instead of replaying a pending mutation as the new user.
  globalThis.location.reload();
}

/** Native-fetch UI API requests must recover from tokens rejected after Operate As. */
export async function fetchWithUiToken(url: string, init: RequestInit): Promise<Response> {
  const response = await fetch(url, init);
  const authorization = new Headers(init.headers).get("Authorization");
  if (response.status === 401 && authorization?.startsWith("Bearer ")) {
    recovery ??= recoverSession(authorization)
      .catch(() => {
        // Preserve the original 401 for the caller's error handling if recovery fails.
        console.warn("Unable to refresh the browser session token.");
      })
      .finally(() => {
        recovery = undefined;
      });
    await recovery;
  }
  return response;
}
