import type { ApiInventoryRecordInfo } from "./inventoryRecordInfo";

export interface ApiInventoryBasketInfo extends ApiInventoryRecordInfo {
  itemCount: number;
}

export interface ApiInventoryBasket extends ApiInventoryBasketInfo {
  contentGlobalIds: string[];
}

export interface ApiInventoryBasketCreateRequest {
  name: string;
}
