export interface ApiInventoryQuantity {
  numericValue: number;
  unitId: number;
}

export type ApiInventoryResultType = "CONTAINER" | "SAMPLE" | "SUBSAMPLE" | "INSTRUMENT" | "INSTRUMENT_TEMPLATE";

export interface ApiInventoryRecordInfo {
  id: number;
  globalId: string;
  name: string;
  quantity?: ApiInventoryQuantity;
}
