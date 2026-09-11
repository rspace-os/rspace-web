import { BaseApiClient } from "./BaseApiClient";

export class StatusClient extends BaseApiClient {
  async isApiKeyValid(): Promise<boolean> {
    const res = await this.request.get("/api/v1/status", { headers: this.headers() });
    return res.ok();
  }
}
