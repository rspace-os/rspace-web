import type { ApiInventoryQuantity, ApiInventoryRecordInfo } from "./inventoryRecordInfo";

export interface ApiInventorySample extends ApiInventoryRecordInfo {
  subSamples: ApiInventorySubSample[];
}

export type ApiInventorySubSample = ApiInventoryRecordInfo;

export interface ApiInventoryBarcode {
  data: string;
  format: string;
  description?: string;
}

export interface ApiInventorySampleCreateRequest {
  name: string;
  newSampleSubSamplesCount?: number;
  barcodes?: ApiInventoryBarcode[];
  /** Sets each new subsample's starting quantity, e.g. { numericValue: 10, unitId: 7 } for 10 g. */
  quantity?: ApiInventoryQuantity;
}

export interface ApiInventoryIdentifierCreateRequest {
  parentGlobalId: string;
}

export interface ApiInventoryIdentifierInfo {
  id: number;
  state: string;
  associatedGlobalId: string;
  /** The provider record id (B2INST RID) or DOI/Handle; the RID before a B2INST PID is minted. */
  doi: string;
}

export interface ApiInventorySampleUpdateRequest {
  name: string;
}

export interface ApiInventorySampleRevisions {
  revisionsCount: number;
  revisions: Array<{ revisionId: number; revisionType: string; record: { version: number } }>;
}

export interface ApiInventorySubSampleMoveRequest {
  parentContainers: [{ id: number }];
  parentLocation: { coordX: number; coordY: number };
}
