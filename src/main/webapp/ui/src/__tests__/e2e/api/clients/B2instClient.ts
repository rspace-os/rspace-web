import type { APIRequestContext } from "@playwright/test";

/** Reads B2INST drafts through its InvenioRDM REST API - the mock server in mock mode, the sandbox in real mode. */
export class B2instClient {
  constructor(private readonly request: APIRequestContext) {}

  /** The instrument name the provider currently holds on draft `rid`. */
  async getDraftName(rid: string): Promise<string | undefined> {
    const res = await this.request.get(`/api/records/${rid}/draft`);
    if (!res.ok()) {
      throw new Error(`getDraftName(${rid}) failed: ${res.status()} ${res.statusText()} — ${await res.text()}`);
    }
    const body = (await res.json()) as { metadata?: { Name?: string } };
    return body.metadata?.Name;
  }
}
