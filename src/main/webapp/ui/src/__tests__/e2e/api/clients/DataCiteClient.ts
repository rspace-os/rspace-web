import type { APIRequestContext } from "@playwright/test";

export type DataCiteMetadata = {
  name: string | undefined;
  metadataVersion: number;
  publisher: string | undefined;
  creators: string[];
};

/** Reads DOIs through DataCite's public REST API - the mock server in mock mode, the test API in real mode. */
export class DataCiteClient {
  constructor(private readonly request: APIRequestContext) {}

  async getMetadata(doi: string): Promise<DataCiteMetadata> {
    const res = await this.request.get(`/dois/${doi}`);
    if (!res.ok()) {
      throw new Error(`getMetadata(${doi}) failed: ${res.status()} ${res.statusText()} — ${await res.text()}`);
    }
    const body = (await res.json()) as {
      data: {
        attributes: {
          titles?: Array<{ title: string }>;
          metadataVersion: number;
          publisher?: string;
          creators?: Array<{ name: string }>;
        };
      };
    };
    const { titles, metadataVersion, publisher, creators } = body.data.attributes;
    return {
      name: titles?.[0]?.title,
      metadataVersion,
      publisher,
      creators: (creators ?? []).map((creator) => creator.name),
    };
  }
}
