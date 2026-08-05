import type {
  ApiInventoryIdentifierCreateRequest,
  ApiInventoryIdentifierInfo,
  ApiInventorySample,
  ApiInventorySampleCreateRequest,
  ApiInventorySampleRevisions,
  ApiInventorySampleUpdateRequest,
  ApiInventorySubSample,
  ApiInventorySubSampleMoveRequest,
} from "../models/inventory";
import type {
  ApiInventoryBasket,
  ApiInventoryBasketCreateRequest,
  ApiInventoryBasketInfo,
} from "../models/inventoryBasket";
import type {
  ApiInventoryContainer,
  ApiInventoryContainerCreateRequest,
  ApiInventoryContainerUpdateRequest,
} from "../models/inventoryContainer";
import { BaseApiClient } from "./BaseApiClient";

export class InventoryClient extends BaseApiClient {
  async createSample(sample: ApiInventorySampleCreateRequest): Promise<ApiInventorySample> {
    return this.requestJson("post", "/api/inventory/v1/samples", { data: sample, action: "createInventorySample" });
  }

  async renameSample(sampleId: number, update: ApiInventorySampleUpdateRequest): Promise<ApiInventorySample> {
    return this.requestJson("put", `/api/inventory/v1/samples/${sampleId}`, {
      data: update,
      action: "renameInventorySample",
    });
  }

  async getSampleVersions(sampleId: number): Promise<number[]> {
    const body = await this.requestJson<ApiInventorySampleRevisions>(
      "get",
      `/api/inventory/v1/samples/${sampleId}/revisions`,
      { action: "getInventorySampleRevisions" },
    );
    const versions = new Set(body.revisions.map((r) => r.record.version));
    return [...versions].sort((a, b) => b - a);
  }

  async createContainer(container: ApiInventoryContainerCreateRequest): Promise<ApiInventoryContainer> {
    return this.requestJson("post", "/api/inventory/v1/containers", {
      data: container,
      action: "createInventoryContainer",
    });
  }

  async updateContainer(
    containerId: number,
    update: ApiInventoryContainerUpdateRequest,
  ): Promise<ApiInventoryContainer> {
    return this.requestJson("put", `/api/inventory/v1/containers/${containerId}`, {
      data: update,
      action: "updateInventoryContainer",
    });
  }

  async moveSubSample(subSampleId: number, move: ApiInventorySubSampleMoveRequest): Promise<ApiInventorySubSample> {
    return this.requestJson("put", `/api/inventory/v1/subSamples/${subSampleId}`, {
      data: move,
      action: "moveInventorySubSample",
    });
  }

  async deleteSubSample(subSampleId: number): Promise<void> {
    await this.requestVoid("delete", `/api/inventory/v1/subSamples/${subSampleId}`, {
      action: "deleteInventorySubSample",
    });
  }

  async createBasket(basket: ApiInventoryBasketCreateRequest): Promise<ApiInventoryBasketInfo> {
    return this.requestJson("post", "/api/inventory/v1/baskets", { data: basket, action: "createInventoryBasket" });
  }

  async addItemsToBasket(basketId: number, globalIds: string[]): Promise<ApiInventoryBasket> {
    return this.requestJson("post", `/api/inventory/v1/baskets/${basketId}/addItems`, {
      data: { globalIds },
      action: "addItemsToInventoryBasket",
    });
  }

  async registerIdentifier(request: ApiInventoryIdentifierCreateRequest): Promise<ApiInventoryIdentifierInfo> {
    return this.requestJson("post", "/api/inventory/v1/identifiers", {
      data: request,
      action: "registerInventoryIdentifier",
    });
  }

  async publishIdentifier(doiId: number): Promise<ApiInventoryIdentifierInfo> {
    return this.requestJson("post", `/api/inventory/v1/identifiers/${doiId}/publish`, {
      action: "publishInventoryIdentifier",
    });
  }

  async retractIdentifier(doiId: number): Promise<ApiInventoryIdentifierInfo> {
    return this.requestJson("post", `/api/inventory/v1/identifiers/${doiId}/retract`, {
      action: "retractInventoryIdentifier",
    });
  }

  async deleteIdentifier(doiId: number): Promise<void> {
    await this.requestVoid("delete", `/api/inventory/v1/identifiers/${doiId}`, {
      action: "deleteInventoryIdentifier",
    });
  }
}
