import { jwtDecode } from "jwt-decode";

export type TokenParams = {
  token?: string;
  getToken?: () => Promise<string>;
};

export const ID_TOKEN_KEY = "id_token";
export const TOKEN_EXPIRY_BUFFER_SECONDS = 300;

const JWT_TOKEN_PATTERN = /^.+\..+\..+$/;

function getSessionStorage(): Storage | null {
  try {
    return typeof globalThis.sessionStorage?.getItem === "function"
      ? globalThis.sessionStorage
      : null;
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

export function secondsToExpiry(token: string): number {
  if (!token.match(JWT_TOKEN_PATTERN)) {
    return Infinity;
  }

  const expiresAt = jwtDecode<{ exp: number }>(token).exp;
  const timeNow = Math.floor(Date.now() / 1000);

  return expiresAt - timeNow;
}

export function isExpiringSoon(
  token: string,
  bufferSeconds = TOKEN_EXPIRY_BUFFER_SECONDS,
): boolean {
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
