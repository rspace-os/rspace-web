import type { APIRequestContext } from "@playwright/test";
import { assertOk } from "@/__tests__/e2e/responses";

export type DataCiteMetadata = {
  name: string | undefined;
  metadataVersion: number;
  publisher: string | undefined;
  creators: string[];
};

/** Reads the provider's metadata independently of RSpace's saved public-page data. */
export class DataCiteClient {
  constructor(private readonly request: APIRequestContext) {}

  async getDoi(doi: string): Promise<unknown> {
    // DataCite omits affiliation identifiers unless this flag is supplied.
    const response = await this.request.get(`/dois/${doi}`, { params: { affiliation: "true" } });
    await assertOk(response, "GET DataCite DOI");
    return ((await response.json()) as { data: unknown }).data;
  }

  async getMetadata(doi: string): Promise<DataCiteMetadata> {
    const response = await this.request.get(`/dois/${doi}`);
    await assertOk(response, `GET DataCite DOI metadata ${doi}`);
    const body = (await response.json()) as {
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
