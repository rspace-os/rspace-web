import type { ApiInventoryGridLocation } from "./inventory";
import type { ApiInventoryRecordInfo } from "./inventoryRecordInfo";

export interface ApiInventoryContainer extends ApiInventoryRecordInfo {
  deleted?: boolean;
  locations?: Array<ApiInventoryGridLocation & { content: ApiInventoryRecordInfo | null }>;
}

export type ApiInventoryContainerType = "LIST" | "GRID" | "IMAGE";

export interface ApiInventoryContainerCreateRequest {
  name: string;
  cType: ApiInventoryContainerType;
  gridLayout?: {
    columnsNumber: number;
    rowsNumber: number;
  };
}

export type ApiInventorySharingMode = "OWNER_GROUPS" | "WHITELIST" | "OWNER_ONLY";

export interface ApiInventorySharedWith {
  group: { id: number };
  shared: boolean;
  itemOwnerGroup?: boolean;
}

export interface ApiInventoryContainerUpdateRequest {
  sharingMode: ApiInventorySharingMode;
  sharedWith: ApiInventorySharedWith[];
}
