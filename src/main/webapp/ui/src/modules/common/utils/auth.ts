import { jwtDecode } from "jwt-decode";
import axios from "@/common/axios";

export type TokenParams = {
  token?: string;
  getToken?: () => Promise<string>;
};

export const ID_TOKEN_KEY = "id_token";
export const TOKEN_EXPIRY_BUFFER_SECONDS = 300;

/* The public view is for a document made accessible to non RSpace users, List Of Materials access initialises AuthStore */
export const isPublicDocumentView = () => document.getElementById("public_document_view") !== null;

const JWT_TOKEN_PATTERN = /^.+\..+\..+$/;
let uiToken: string | null = null;
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
  uiToken = token;
}

async function postUiToken(): Promise<string> {
  const response = await axios.post<{ accessToken: string }>("/api/v2/oauth/tokens");
  return response.data.accessToken;
}

/**
 * Shares a session-bound token for this page, including overlapping mint requests. `mint` lets a
 * caller keep its own transport; every transport posts to the same session-bound token endpoint.
 */
export function mintUiToken(mint: () => Promise<string> = postUiToken): Promise<string> {
  if (uiToken && !isExpiringSoon(uiToken)) return Promise.resolve(uiToken);
  return fetchOauthToken(async () => {
    const token = await mint();
    replaceUiToken(token);
    return token;
  });
}

/** Clears page-local state between tests. */
export function resetUiTokenForTests(): void {
  uiToken = null;
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

export const resolveToken = async ({ token, getToken }: TokenParams) => {
  if (token) {
    return token;
  }
  if (getToken) {
    return getToken();
  }
  throw new Error("Token is required to perform this operation");
};
