import { jwtDecode } from "jwt-decode";
import { getApiV2TokenProvider } from "@/modules/common/api/v2/tokenProvider";

export type TokenParams = {
  token?: string;
  getToken?: () => Promise<string>;
};

export const ID_TOKEN_KEY = "id_token";
export const TOKEN_EXPIRY_BUFFER_SECONDS = 300;

/* The public view is for a document made accessible to non RSpace users, List Of Materials access initialises AuthStore */
export const isPublicDocumentView = () => document.getElementById("public_document_view") !== null;

const JWT_TOKEN_PATTERN = /^.+\..+\..+$/;
let pendingOauthTokenRequest: Promise<string> | null = null;

function getSessionStorage(): Storage | null {
  try {
    return typeof globalThis.sessionStorage?.getItem === "function" ? globalThis.sessionStorage : null;
  } catch {
    return null;
  }
}

export function getStoredToken(): string | null {
  try {
    return getSessionStorage()?.getItem(ID_TOKEN_KEY) ?? null;
  } catch {
    return null;
  }
}

export function saveStoredToken(token: string): void {
  try {
    getSessionStorage()?.setItem(ID_TOKEN_KEY, token);
  } catch {
    // Ignore storage access failures in non-browser or restricted environments.
  }
}

export function clearStoredToken(): void {
  try {
    getSessionStorage()?.removeItem(ID_TOKEN_KEY);
  } catch {
    // Ignore storage access failures in non-browser or restricted environments.
  }
}

/**
 * @deprecated For new usages, use the TanStack Query `useOauthTokenQuery` hook instead.
 */
export function fetchOauthToken(fetchToken: () => Promise<string>): Promise<string> {
  pendingOauthTokenRequest ??= fetchToken().finally(() => {
    pendingOauthTokenRequest = null;
  });
  return pendingOauthTokenRequest;
}

/** Replaces the page token after a deliberate recovery mint. */
export function replaceUiToken(token: string): void {
  saveStoredToken(token);
}

/** Gets the page's session-bound token from the shared provider. */
/** Mints or reuses the page's UI token; aborting the signal rejects this caller only, never the shared mint. */
export function mintUiToken(signal?: AbortSignal): Promise<string> {
  return getApiV2TokenProvider().getToken(signal);
}

/** Clears page-local state between tests. */
export function resetUiTokenForTests(): void {
  pendingOauthTokenRequest = null;
}

export function secondsToExpiry(token: string): number {
  if (!token.match(JWT_TOKEN_PATTERN)) {
    return Infinity;
  }

  const expiresAt = jwtDecode<{ exp: number }>(token).exp;
  const timeNow = Math.floor(Date.now() / 1000);

  return expiresAt - timeNow;
}

export function isExpiringSoon(token: string, bufferSeconds = TOKEN_EXPIRY_BUFFER_SECONDS): boolean {
  return secondsToExpiry(token) < bufferSeconds;
}

const MIN_REFRESH_DELAY_SECONDS = 60;
const MAX_SET_TIMEOUT_DELAY_MS = 2_147_483_647;

/**
 * How long to wait before refreshing the token: while it is still valid, at the margin isExpiringSoon uses.
 * Expiry is measured against the client clock, so the floor stops a clock running ~an hour fast from
 * refreshing in a tight loop.
 */
export function tokenRefreshDelayMs(token: string): number {
  return Math.min(
    Math.max(MIN_REFRESH_DELAY_SECONDS, secondsToExpiry(token) - TOKEN_EXPIRY_BUFFER_SECONDS) * 1000,
    MAX_SET_TIMEOUT_DELAY_MS,
  );
}

export const resolveToken = async ({ token, getToken }: TokenParams) => {
  if (token) {
    return token;
  }
  if (getToken) {
    return getToken();
  }
  throw new Error("Token is required to perform this operation");
};
