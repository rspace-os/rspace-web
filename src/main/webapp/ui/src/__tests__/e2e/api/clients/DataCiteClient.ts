import type { APIRequestContext } from "@playwright/test";
import { assertOk } from "@/__tests__/e2e/responses";

/** Reads the provider's metadata independently of RSpace's saved public-page data. */
export class DataCiteClient {
  constructor(private readonly request: APIRequestContext) {}

  async getDoi(doi: string): Promise<unknown> {
    const path = doi.split("/").map(encodeURIComponent).join("/");
    // DataCite omits affiliation identifiers unless this flag is supplied.
    const response = await this.request.get(`/dois/${path}`, { params: { affiliation: "true" } });
    await assertOk(response, "GET DataCite DOI");
    return ((await response.json()) as { data: unknown }).data;
  }
}
