export interface ApiDocumentField {
  id: number;
  name: string;
  type: string;
  content?: string;
}

export interface ApiDocument {
  id: number;
  globalId: string;
  name: string;
  parentFolderId?: number;
  tags?: string;
  fields: ApiDocumentField[];
}

export interface ApiDocumentCreateRequest {
  name: string;
  form?: { id: number };
  parentFolderId?: number;
  tags?: string;
  fields?: Array<{ content: string }>;
}

export interface ApiDocumentUpdateRequest {
  name?: string;
  fields?: Array<{ id: number; content: string }>;
}
