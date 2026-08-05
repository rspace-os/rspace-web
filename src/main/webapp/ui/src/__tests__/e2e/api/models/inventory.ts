import type { ApiInventoryRecordInfo } from "./inventoryRecordInfo";

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
}

export interface ApiInventoryIdentifierCreateRequest {
  parentGlobalId: string;
}

export interface ApiInventoryIdentifierInfo {
  id: number;
  state: string;
  associatedGlobalId: string;
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
