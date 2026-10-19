import type { ApiStoichiometry } from "../models/stoichiometry";
import { BaseApiClient } from "./BaseApiClient";

export class StoichiometryClient extends BaseApiClient {
  async getById(stoichiometryId: number): Promise<ApiStoichiometry> {
    return this.requestJson("get", `/api/v1/stoichiometry?stoichiometryId=${stoichiometryId}`, {
      action: "getStoichiometry",
    });
  }
}
