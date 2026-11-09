import { jwtDecode } from "jwt-decode";
import {
  clearStoredToken,
  isPublicDocumentView,
  saveStoredToken,
  TOKEN_EXPIRY_BUFFER_SECONDS,
  tokenRefreshDelayMs,
} from "../../utils/auth";
import { ApiV2ProblemError } from "./ApiV2ProblemError";
import { createUiOAuthToken } from "./generated/oauth/oauth";

export class ApiV2IdentityChangedError extends Error {
  constructor(message = "Browser identity changed") {
    super(message);
    this.name = "ApiV2IdentityChangedError";
  }
}

export interface ApiV2TokenProvider {
  getToken(signal?: AbortSignal): Promise<string>;
  recover(rejected: string): Promise<string>;
}

type Claims = { exp?: number; refreshTokenHash?: string };
function claims(token: string): Claims {
  try {
    return jwtDecode<Claims>(token);
  } catch {
    return {};
  }
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

class SessionTokenProvider implements ApiV2TokenProvider {
  private token?: string;
  private minting?: Promise<string>;
  private recovering = new Map<string, Promise<string>>();
  private timer?: ReturnType<typeof setTimeout>;
  private identityChanged = false;

  private checkIdentity(next: string): void {
    const previousHash = this.token && claims(this.token).refreshTokenHash;
    const nextHash = claims(next).refreshTokenHash;
    if (this.identityChanged) throw new ApiV2IdentityChangedError();
    if (previousHash && nextHash && previousHash !== nextHash) {
      this.identityChanged = true;
      clearTimeout(this.timer);
      clearStoredToken();
      globalThis.location.reload();
      throw new ApiV2IdentityChangedError();
    }
  }

  private schedule(token: string): void {
    clearTimeout(this.timer);
    if (!Number.isFinite(claims(token).exp)) return;
    this.timer = setTimeout(() => {
      void this.mint().catch(() => {
        if (!this.identityChanged) {
          console.warn("Unable to refresh the browser session token.");
          this.timer = setTimeout(() => this.schedule(token), 60_000);
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
    const exp = this.token ? claims(this.token).exp : undefined;
    const token = this.token;
    const fresh = token && (exp === undefined || exp - Date.now() / 1000 > TOKEN_EXPIRY_BUFFER_SECONDS);
    return abortable(fresh ? Promise.resolve(token) : this.mint(), signal);
  }

  recover(rejected: string): Promise<string> {
    if (this.identityChanged) return Promise.reject(new ApiV2IdentityChangedError());
    if (this.token && this.token !== rejected) return Promise.resolve(this.token);
    let recovery = this.recovering.get(rejected);
    if (!recovery) {
      recovery = (async () => {
        const response = await fetch("/api/v1/userDetails/whoami", {
          headers: { Authorization: `Bearer ${rejected}`, "X-Requested-With": "XMLHttpRequest" },
        });
        if (response.status !== 401) return rejected;
        if (isPublicDocumentView()) {
          this.identityChanged = true;
          clearTimeout(this.timer);
          clearStoredToken();
          globalThis.location.reload();
          throw new ApiV2IdentityChangedError("Public document session expired");
        }
        try {
          return await this.mint();
        } catch (error) {
          if (error instanceof ApiV2ProblemError && (error.status === 401 || error.status === 403)) {
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
