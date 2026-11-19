import { jwtDecode } from "jwt-decode";
import i18n from "@/modules/common/i18n";
import { isExpiringSoon } from "../../utils/auth";
import { ApiV2ProblemError, ApiV2SessionEndedError, ApiV2UnexpectedResponseError } from "./ApiV2ProblemError";
import { createUiOAuthToken } from "./generated/oauth/oauth";

export class ApiV2IdentityChangedError extends Error {
  constructor() {
    super(i18n.t("common:apiErrors.apiV2.identityChanged"));
    this.name = "ApiV2IdentityChangedError";
  }
}

export interface ApiV2TokenProvider {
  getToken(signal?: AbortSignal): Promise<string>;
  /** Replaces a token that API v2 rejected with 401, or resolves with the token that already replaced it. */
  recover(rejected: string): Promise<string>;
}

/** What the page does when the provider can no longer deliver tokens for the current session. */
export interface ApiV2SessionHandlers {
  /** Token issuance refused the session (401 or 403, or the HTML login page). */
  onSessionEnded(error: ApiV2SessionEndedError): void;
  /** The session now belongs to another identity. */
  onIdentityChanged(): void;
}

/** For entry points without handlers of their own: session ended navigates to `/login`, identity changed reloads. */
export const defaultApiV2SessionHandlers: ApiV2SessionHandlers = {
  onSessionEnded: () => globalThis.location.assign("/login"),
  onIdentityChanged: () => globalThis.location.reload(),
};

/** The session a UI token belongs to; tokens without one (or malformed tokens) yield undefined. */
function refreshTokenHash(token: string): string | undefined {
  try {
    const hash = jwtDecode<{ refreshTokenHash?: unknown }>(token).refreshTokenHash;
    return typeof hash === "string" && hash ? hash : undefined;
  } catch {
    return undefined;
  }
}

/** Token issuance answers like this only when the browser session has ended. */
function sessionEnded(error: unknown): error is ApiV2ProblemError | ApiV2UnexpectedResponseError {
  return (
    (error instanceof ApiV2ProblemError && (error.status === 401 || error.status === 403)) ||
    (error instanceof ApiV2UnexpectedResponseError && error.reason === "undeclared" && error.media === "text/html")
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

/**
 * Mints session-bound tokens on demand: concurrent callers share one mint, a token near expiry is replaced before use,
 * and a token for another identity stops all delivery. Navigation is left to the session handlers.
 */
export class SessionTokenProvider implements ApiV2TokenProvider {
  private token?: string;
  private minting?: Promise<string>;
  private identityChanged = false;

  constructor(private readonly handlers: ApiV2SessionHandlers) {}

  /** Stops delivering tokens and lets the page discard every state of the previous identity. */
  private abandonIdentity(): never {
    this.identityChanged = true;
    this.handlers.onIdentityChanged();
    throw new ApiV2IdentityChangedError();
  }

  private checkIdentity(next: string): void {
    if (this.identityChanged) throw new ApiV2IdentityChangedError();
    const previousHash = this.token && refreshTokenHash(this.token);
    const nextHash = refreshTokenHash(next);
    if (previousHash && nextHash && previousHash !== nextHash) this.abandonIdentity();
  }

  /** Gives a refused mint its outcome; failures that are not a refusal reach the caller unchanged. */
  private mintFailure(error: unknown): unknown {
    if (!sessionEnded(error)) return error;
    const ended = new ApiV2SessionEndedError(error.status, error);
    this.handlers.onSessionEnded(ended);
    return ended;
  }

  private mint(): Promise<string> {
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    this.minting ??= (async () => {
      let token: string;
      try {
        token = (await createUiOAuthToken()).accessToken;
      } catch (error) {
        throw this.mintFailure(error);
      }
      if (!token) throw new ApiV2UnexpectedResponseError("invalidBody");
      this.checkIdentity(token);
      this.token = token;
      return token;
    })().finally(() => {
      this.minting = undefined;
    });
    return this.minting;
  }

  getToken(signal?: AbortSignal): Promise<string> {
    signal?.throwIfAborted();
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    // A mint in flight (an expiry-driven refresh or a recovery) supersedes the cached token.
    const token = this.minting ? undefined : this.token;
    return abortable(token && !isExpiringSoon(token) ? Promise.resolve(token) : this.mint(), signal);
  }

  recover(rejected: string): Promise<string> {
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    if (this.token && this.token !== rejected) return Promise.resolve(this.token);
    // API v2 answers 401 only when it rejects the credentials; a refused permission is 403.
    return this.mint();
  }
}

let provider: ApiV2TokenProvider | undefined;

/** The provider the page's entry point installed. A token-bearing call fails fast when none was installed. */
export function getApiV2TokenProvider(): ApiV2TokenProvider {
  if (!provider) {
    throw new Error("No API v2 token provider is installed; the page's entry point must call setApiV2TokenProvider.");
  }
  return provider;
}

/**
 * Installs the page's provider and returns a function restoring the previous one. Each entry point that makes
 * token-bearing calls installs one before rendering; tests swap it.
 */
export function setApiV2TokenProvider(next: ApiV2TokenProvider): () => void {
  const previous = provider;
  provider = next;
  return () => {
    provider = previous;
  };
}
