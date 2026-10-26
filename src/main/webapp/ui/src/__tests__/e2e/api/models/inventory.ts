import type { ApiInventoryQuantity, ApiInventoryRecordInfo } from "./inventoryRecordInfo";

export interface ApiInventorySample extends ApiInventoryRecordInfo {
  subSamples: ApiInventorySubSample[];
}

export interface ApiInventoryGridLocation {
  coordX: number;
  coordY: number;
}

export interface ApiInventorySubSample extends ApiInventoryRecordInfo {
  deleted?: boolean;
  parentContainers?: Array<{ id: number; globalId: string; name: string; cType: string }>;
  parentLocation?: ApiInventoryGridLocation | null;
}

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

export interface ApiInventorySearchResult {
  totalHits: number;
  records: ApiInventoryRecordInfo[];
}

export interface ApiInventoryBulkMoveRecord {
  id: number;
  globalId: string;
  type: "SUBSAMPLE" | "CONTAINER";
  parentContainers: [{ id: number }];
  parentLocation?: ApiInventoryGridLocation;
}

export interface ApiInventoryBulkResult {
  status: string;
  successCount: number;
  errorCount: number;
  results: Array<{ record: ApiInventoryRecordInfo | null; error: { errors: string[] } | null }>;
}

export interface ApiInventoryAliquotRequest {
  origin: { globalId: string; amountTaken: ApiInventoryQuantity };
  sampleName: string;
  count: number;
  eachAmount: ApiInventoryQuantity;
}

export interface ApiInventorySubSampleMoveRequest {
  parentContainers: [{ id: number }];
  /** Required for a grid container, omitted for a list container. */
  parentLocation?: { coordX: number; coordY: number };
}
