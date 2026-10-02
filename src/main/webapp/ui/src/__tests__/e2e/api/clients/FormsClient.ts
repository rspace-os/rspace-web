import type { ApiForm, ApiFormCreate } from "../models/form";
import { BaseApiClient } from "./BaseApiClient";

export class FormsClient extends BaseApiClient {
  async create(form: ApiFormCreate): Promise<ApiForm> {
    return this.requestJson("post", "/api/v1/forms", { data: form, action: "createForm" });
  }
}
