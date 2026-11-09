import * as v from "valibot";
import {
  clearStoredToken,
  isExpiringSoon,
  isPublicDocumentView,
  refreshTokenHash,
  saveStoredToken,
  secondsToExpiry,
  tokenRefreshDelayMs,
} from "../../utils/auth";
import { ApiV2ProblemError, ApiV2UnexpectedResponseError } from "./ApiV2ProblemError";
import { createUiOAuthToken } from "./generated/oauth/oauth";

export class ApiV2IdentityChangedError extends Error {
  constructor(message = "Browser identity changed") {
    super(message);
    this.name = "ApiV2IdentityChangedError";
  }
}

export interface ApiV2TokenProvider {
  getToken(signal?: AbortSignal): Promise<string>;
  /**
   * Replaces a rejected token, or resolves with it while it is still valid (a permission 401). `invalid` skips the
   * identity probe when the probe itself rejected the token.
   */
  recover(rejected: string, invalid?: boolean): Promise<string>;
}

const REFRESH_RETRY_DELAY_MS = 60_000;
const refreshListeners = new Set<() => void>();

/** The session provider owns the refresh timer; listeners run as each scheduled refresh starts. */
export function onApiV2TokenRefresh(listener: () => void): () => void {
  refreshListeners.add(listener);
  return () => {
    refreshListeners.delete(listener);
  };
}

/** Token issuance answers like this only when the browser session has ended. */
function sessionEnded(error: unknown): boolean {
  return (
    (error instanceof ApiV2ProblemError && (error.status === 401 || error.status === 403)) ||
    error instanceof ApiV2UnexpectedResponseError ||
    v.isValiError(error)
  );
}

function abortable<T>(pending: Promise<T>, signal?: AbortSignal): Promise<T> {
  if (!signal) return pending;
  signal.throwIfAborted();
  return new Promise((resolve, reject) => {
    const abort = () => reject(signal.reason);
    signal.addEventListener("abort", abort, { once: true });
    pending.then(resolve, reject).finally(() => signal.removeEventListener("abort", abort));
  });
}

export class SessionTokenProvider implements ApiV2TokenProvider {
  private token?: string;
  private minting?: Promise<string>;
  private recovering = new Map<string, Promise<string>>();
  private timer?: ReturnType<typeof setTimeout>;
  private identityChanged = false;

  /** Stops delivering tokens and reloads, so no state of the previous identity survives. */
  private abandonIdentity(message?: string): never {
    this.identityChanged = true;
    clearTimeout(this.timer);
    clearStoredToken();
    globalThis.location.reload();
    throw new ApiV2IdentityChangedError(message);
  }

  private checkIdentity(next: string): void {
    if (this.identityChanged) throw new ApiV2IdentityChangedError();
    const previousHash = this.token && refreshTokenHash(this.token);
    const nextHash = refreshTokenHash(next);
    if (previousHash && nextHash && previousHash !== nextHash) this.abandonIdentity();
  }

  private schedule(token: string): void {
    clearTimeout(this.timer);
    if (!Number.isFinite(secondsToExpiry(token))) return;
    this.timer = setTimeout(() => {
      const refresh = this.mint();
      for (const listener of refreshListeners) listener();
      void refresh.catch(() => {
        if (!this.identityChanged) {
          console.warn("Unable to refresh the browser session token.");
          // Rescheduling the expired token adds its one-minute floor, so the retry fires after two minutes.
          this.timer = setTimeout(() => this.schedule(token), REFRESH_RETRY_DELAY_MS);
        }
      });
    }, tokenRefreshDelayMs(token));
  }

  private mint(): Promise<string> {
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    this.minting ??= (async () => {
      let token: string;
      if (isPublicDocumentView()) {
        const response = await fetch("/public/publicView/userform/ajax/inventoryOauthToken", {
          headers: { "X-Requested-With": "XMLHttpRequest" },
        });
        if (!response.ok) throw new ApiV2ProblemError(response.status, undefined, response.statusText);
        const body: unknown = await response.json();
        if (!body || typeof body !== "object" || !("data" in body) || typeof body.data !== "string") {
          throw new TypeError("Invalid public document token");
        }
        token = body.data;
      } else {
        token = (await createUiOAuthToken()).accessToken;
      }
      if (!token) throw new TypeError("Empty browser session token");
      this.checkIdentity(token);
      this.token = token;
      if (isPublicDocumentView()) saveStoredToken(token);
      this.schedule(token);
      return token;
    })().finally(() => {
      this.minting = undefined;
    });
    return this.minting;
  }

  getToken(signal?: AbortSignal): Promise<string> {
    signal?.throwIfAborted();
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    // A mint in flight (a scheduled refresh or a recovery) supersedes the cached token.
    const token = this.minting ? undefined : this.token;
    return abortable(token && !isExpiringSoon(token) ? Promise.resolve(token) : this.mint(), signal);
  }

  recover(rejected: string, invalid = false): Promise<string> {
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    if (this.token && this.token !== rejected) return Promise.resolve(this.token);
    let recovery = this.recovering.get(rejected);
    if (!recovery) {
      recovery = (async () => {
        // Resource permission failures also use 401. Only replace a token the server no longer accepts.
        if (!invalid) {
          const response = await fetch("/api/v1/userDetails/whoami", {
            headers: { Authorization: `Bearer ${rejected}`, "X-Requested-With": "XMLHttpRequest" },
          });
          if (response.status !== 401) return rejected;
        }
        // The public controller restores the anonymous session when the document reloads.
        if (isPublicDocumentView()) this.abandonIdentity("Public document session expired");
        try {
          return await this.mint();
        } catch (error) {
          if (sessionEnded(error)) {
            clearStoredToken();
            globalThis.location.assign("/login");
          }
          throw error;
        }
      })().finally(() => {
        this.recovering.delete(rejected);
      });
      this.recovering.set(rejected, recovery);
    }
    return recovery;
  }
}

let provider: ApiV2TokenProvider = new SessionTokenProvider();
export function getApiV2TokenProvider(): ApiV2TokenProvider {
  return provider;
}
export function setApiV2TokenProvider(next: ApiV2TokenProvider): () => void {
  const previous = provider;
  provider = next;
  return () => {
    provider = previous;
  };
}
