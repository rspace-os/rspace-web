import type { APIRequestContext } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { assertAjaxSuccess, assertOk } from "@/__tests__/e2e/responses";

// The endpoints take the ROR ID in the path, with each "/" replaced by this marker (ValidRoRID).
const SLASH_MARKER = "__rspacror_forsl__";

export class RorRegistryClient {
  constructor(private readonly request: APIRequestContext) {}

  async linkedRorId(): Promise<string> {
    const response = await this.request.get("/system/ror/existingGlobalRoRID");
    await assertOk(response, "GET");
    return (await response.text()).trim();
  }

  async setLinkedRorId(rorId: string): Promise<void> {
    const options = { headers: { Referer: env.baseURL } };
    const response = rorId
      ? await this.request.post(`/system/ror/rorForID/${rorId.replaceAll("/", SLASH_MARKER)}`, options)
      : await this.request.delete("/system/ror/rorForID/", options);
    await assertOk(response, rorId ? "POST" : "DELETE");
    assertAjaxSuccess(await response.text(), `Setting ROR ID '${rorId}'`);
  }
}
