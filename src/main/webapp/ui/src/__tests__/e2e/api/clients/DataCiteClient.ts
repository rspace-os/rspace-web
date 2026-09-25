import type { APIRequestContext } from "@playwright/test";

/** Reads DOIs through DataCite's public REST API - the mock server in mock mode, the test API in real mode. */
export class DataCiteClient {
  constructor(private readonly request: APIRequestContext) {}

  /** DataCite's own count of metadata updates to `doi`, bumped once per accepted PUT. */
  async getMetadataVersion(doi: string): Promise<number> {
    const res = await this.request.get(`/dois/${doi}`);
    if (!res.ok()) {
      throw new Error(`getMetadataVersion(${doi}) failed: ${res.status()} ${res.statusText()} — ${await res.text()}`);
    }
    const body = (await res.json()) as { data: { attributes: { metadataVersion: number } } };
    return body.data.attributes.metadataVersion;
  }
}
