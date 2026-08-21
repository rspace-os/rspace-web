import type { ApiInventoryRecordInfo } from "./inventoryRecordInfo";

export type ApiInventoryInstrument = ApiInventoryRecordInfo;

export interface ApiInventoryInstrumentField {
  name?: string;
  type?: string;
  content?: string;
}

export interface ApiInventoryInstrumentCreateRequest {
  name: string;
  templateId?: number;
  fields?: ApiInventoryInstrumentField[];
}

export type ApiInventoryInstrumentTemplate = ApiInventoryRecordInfo;

export interface ApiInventoryInstrumentTemplateFieldDefinition {
  name: string;
  type: string;
  content?: string;
  definition?: { options: string[] };
}

export interface ApiInventoryInstrumentTemplateCreateRequest {
  name: string;
  fields?: ApiInventoryInstrumentTemplateFieldDefinition[];
}
