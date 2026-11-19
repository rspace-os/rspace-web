import { jwtDecode } from "jwt-decode";
import i18n from "@/modules/common/i18n";
import { clearStoredToken, isExpiringSoon, saveStoredToken } from "../../utils/auth";
import {
  ApiV2ProblemError,
  ApiV2SessionEndedError,
  ApiV2UnexpectedResponseError,
  httpStatusMessage,
  readJsonBody,
  responseMedia,
  transportFailure,
} from "./ApiV2ProblemError";
import { createUiOAuthToken } from "./generated/oauth/oauth";

export class ApiV2IdentityChangedError extends Error {
  constructor() {
    super(i18n.t("common:apiErrors.apiV2.identityChanged"));
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

/** What the page does when the provider can no longer deliver tokens for the current session. */
export interface ApiV2SessionHandlers {
  /** Token issuance refused the session (401 or 403, or the HTML login page). */
  onSessionEnded(error: ApiV2SessionEndedError): void;
  /** The session now belongs to another identity, or a public document session expired. */
  onIdentityChanged(): void;
}

const defaultSessionHandlers: ApiV2SessionHandlers = {
  onSessionEnded: () => {
    clearStoredToken();
    globalThis.location.assign("/login");
  },
  onIdentityChanged: () => {
    clearStoredToken();
    globalThis.location.reload();
  },
};
let installedSessionHandlers = defaultSessionHandlers;

/**
 * Installs the session handlers that the module-level provider uses, read at call time; returns a function restoring
 * the previous ones. Entry points that install nothing keep the defaults: session ended clears the stored token and
 * navigates to `/login`, identity changed clears it and reloads.
 */
export function setApiV2SessionHandlers(next: ApiV2SessionHandlers): () => void {
  const previous = installedSessionHandlers;
  installedSessionHandlers = next;
  return () => {
    installedSessionHandlers = previous;
  };
}

/* The public view is for a document made accessible to non RSpace users. */
const isPublicDocumentView = () => document.getElementById("public_document_view") !== null;

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

async function mintPublicDocumentToken(): Promise<string> {
  let response: Response;
  try {
    response = await fetch("/public/publicView/userform/ajax/inventoryOauthToken", {
      headers: { "X-Requested-With": "XMLHttpRequest" },
    });
  } catch (error) {
    throw transportFailure(error);
  }
  if (!response.ok) throw new ApiV2ProblemError(response.status, undefined, httpStatusMessage(response.status));
  const media = responseMedia(response);
  // A redirect to the login page answers successfully with HTML.
  if (media === "text/html") throw new ApiV2UnexpectedResponseError("undeclared", { status: response.status, media });
  const body = await readJsonBody(response);
  if (!body || typeof body !== "object" || !("data" in body) || typeof body.data !== "string") {
    throw new ApiV2UnexpectedResponseError("invalidBody", { status: response.status, media });
  }
  return body.data;
}

/**
 * Mints session-bound tokens on demand: concurrent callers share one mint, a token near expiry is replaced before use,
 * and a token for another identity stops all delivery. Navigation is left to the session handlers.
 */
export class SessionTokenProvider implements ApiV2TokenProvider {
  private token?: string;
  private minting?: Promise<string>;
  private recovering = new Map<string, Promise<string>>();
  private identityChanged = false;

  /** Without handlers, the provider reads those installed through `setApiV2SessionHandlers` at call time. */
  constructor(private readonly handlers?: ApiV2SessionHandlers) {}

  private get sessionHandlers(): ApiV2SessionHandlers {
    return this.handlers ?? installedSessionHandlers;
  }

  /** Stops delivering tokens and lets the page discard every state of the previous identity. */
  private abandonIdentity(): never {
    this.identityChanged = true;
    this.sessionHandlers.onIdentityChanged();
    throw new ApiV2IdentityChangedError();
  }

  private checkIdentity(next: string): void {
    if (this.identityChanged) throw new ApiV2IdentityChangedError();
    const previousHash = this.token && refreshTokenHash(this.token);
    const nextHash = refreshTokenHash(next);
    if (previousHash && nextHash && previousHash !== nextHash) this.abandonIdentity();
  }

  /** Gives a refused mint its outcome; failures that are not a refusal reach the caller unchanged. */
  private mintFailure(error: unknown, publicView: boolean, hadToken: boolean): unknown {
    if (!sessionEnded(error)) return error;
    if (publicView) {
      // Some sessions are always refused (run-as, login-disabled users), so only a lost session reloads.
      if (hadToken) this.abandonIdentity();
      return error;
    }
    const ended = new ApiV2SessionEndedError(error.status, error);
    this.sessionHandlers.onSessionEnded(ended);
    return ended;
  }

  private mint(): Promise<string> {
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    this.minting ??= (async () => {
      const publicView = isPublicDocumentView();
      let token: string;
      try {
        token = publicView ? await mintPublicDocumentToken() : (await createUiOAuthToken()).accessToken;
      } catch (error) {
        throw this.mintFailure(error, publicView, this.token !== undefined);
      }
      if (!token) throw new ApiV2UnexpectedResponseError("invalidBody");
      this.checkIdentity(token);
      this.token = token;
      if (publicView) saveStoredToken(token);
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
        if (isPublicDocumentView()) this.abandonIdentity();
        return this.mint();
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
/**
 * Replaces the module-level provider and returns a function restoring the previous one. For tests only (`setup.ts`,
 * the provider and transport tests, and query tests); application code always uses the session provider.
 */
export function setApiV2TokenProvider(next: ApiV2TokenProvider): () => void {
  const previous = provider;
  provider = next;
  return () => {
    provider = previous;
  };
}
