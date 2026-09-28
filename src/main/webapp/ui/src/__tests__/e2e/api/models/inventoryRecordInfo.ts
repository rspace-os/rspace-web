export interface ApiInventoryQuantity {
  numericValue: number;
  unitId: number;
}

export interface ApiInventoryRecordInfo {
  id: number;
  globalId: string;
  name: string;
  quantity?: ApiInventoryQuantity;
}
