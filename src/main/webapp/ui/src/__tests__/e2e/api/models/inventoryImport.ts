export interface ApiInventoryImportInstrumentsSettings {
  templateId?: number;
  fieldMappings?: Record<string, string>;
}

export interface ApiInventoryImportRecord {
  id: number;
  globalId: string;
  name: string;
}

export interface ApiInventoryImportRecordResult {
  record?: ApiInventoryImportRecord | null;
  error?: { message?: string } | null;
}

export interface ApiInventoryImportPartialResult {
  status: string;
  successCount: number;
  errorCount: number;
  results: ApiInventoryImportRecordResult[];
}

export interface ApiInventoryImportResult {
  status: string;
  instrumentResults?: ApiInventoryImportPartialResult;
}
