import type { APIRequestContext, APIResponse } from "@playwright/test";

export class BaseApiClient {
  constructor(
    protected readonly request: APIRequestContext,
    protected readonly apiKey: string,
  ) {}

  protected headers(): Record<string, string> {
    return { apiKey: this.apiKey };
  }

  protected async assertOk(res: APIResponse, action: string): Promise<void> {
    if (!res.ok()) {
      throw new Error(`${action} failed: ${res.status()} ${res.statusText()} — ${await res.text()}`);
    }
  }

  protected async requestJson<T>(
    method: "get" | "post" | "put" | "delete",
    url: string,
    options: { data?: unknown; action: string },
  ): Promise<T> {
    const res = await this.request[method](url, {
      headers: this.headers(),
      ...(options.data !== undefined ? { data: options.data } : {}),
    });
    await this.assertOk(res, options.action);
    return res.json() as Promise<T>;
  }

  protected async requestVoid(
    method: "get" | "post" | "put" | "delete",
    url: string,
    options: { data?: unknown; action: string },
  ): Promise<void> {
    const res = await this.request[method](url, {
      headers: this.headers(),
      ...(options.data !== undefined ? { data: options.data } : {}),
    });
    await this.assertOk(res, options.action);
  }
}
