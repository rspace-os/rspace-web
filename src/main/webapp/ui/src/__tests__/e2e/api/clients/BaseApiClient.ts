import type { APIRequestContext, APIResponse } from "@playwright/test";

/**
 * Base class for one-class-per-resource API clients under `api/clients/`.
 * Holds the shared `APIRequestContext` and the `apiKey` auth header — see
 * `com.researchspace.api.v1.auth.ApiKeyAuthenticator` on the server side,
 * which reads the key from a header literally named `apiKey`.
 */
export class BaseApiClient {
  constructor(
    protected readonly request: APIRequestContext,
    protected readonly apiKey: string,
  ) {}

  protected headers(): Record<string, string> {
    return { apiKey: this.apiKey };
  }

  /**
   * Throws with the response status, status text, and body if `res` is not a
   * 2xx — call after every request so failures surface immediately instead of
   * as a downstream `res.json()` parse error.
   */
  protected async assertOk(res: APIResponse, action: string): Promise<void> {
    if (!res.ok()) {
      throw new Error(`${action} failed: ${res.status()} ${res.statusText()} — ${await res.text()}`);
    }
  }
}
