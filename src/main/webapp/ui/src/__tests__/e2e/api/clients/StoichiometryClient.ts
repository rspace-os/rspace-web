import type { APIResponse } from "@playwright/test";
import type {
  ApiStockDeductionRequest,
  ApiStockDeductionResult,
  ApiStoichiometry,
  ApiStoichiometryUpdateRequest,
} from "../models/stoichiometry";
import { BaseApiClient } from "./BaseApiClient";

export class StoichiometryClient extends BaseApiClient {
  async getById(stoichiometryId: number): Promise<ApiStoichiometry> {
    return this.requestJson("get", `/api/v1/stoichiometry?stoichiometryId=${stoichiometryId}`, {
      action: "getStoichiometry",
    });
  }

  async update(
    stoichiometryId: number,
    update: ApiStoichiometryUpdateRequest,
    updateFieldHtml = false,
  ): Promise<ApiStoichiometry> {
    return this.requestJson(
      "put",
      `/api/v1/stoichiometry?stoichiometryId=${stoichiometryId}&updateFieldHtml=${updateFieldHtml}`,
      { data: update, action: "updateStoichiometry" },
    );
  }

  /** Raw, unasserted response -- for tests exercising the 409 edit-conflict path. */
  async updateRaw(
    stoichiometryId: number,
    update: ApiStoichiometryUpdateRequest,
    updateFieldHtml = false,
  ): Promise<APIResponse> {
    return this.request.put(
      `/api/v1/stoichiometry?stoichiometryId=${stoichiometryId}&updateFieldHtml=${updateFieldHtml}`,
      { headers: this.headers(), data: update },
    );
  }

  async remove(stoichiometryId: number, updateFieldHtml = false): Promise<boolean> {
    return this.requestJson(
      "delete",
      `/api/v1/stoichiometry?stoichiometryId=${stoichiometryId}&updateFieldHtml=${updateFieldHtml}`,
      { action: "deleteStoichiometry" },
    );
  }

  async deductStock(request: ApiStockDeductionRequest): Promise<ApiStockDeductionResult> {
    return this.requestJson("post", "/api/v1/stoichiometry/link/deductStock", {
      data: request,
      action: "deductStock",
    });
  }
}
