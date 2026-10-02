import type { ApiDocument, ApiDocumentCreateRequest, ApiDocumentUpdateRequest } from "../models/document";
import { BaseApiClient } from "./BaseApiClient";

export class DocumentsClient extends BaseApiClient {
  async create(doc: ApiDocumentCreateRequest): Promise<ApiDocument> {
    return this.requestJson("post", "/api/v1/documents", { data: doc, action: "createDocument" });
  }

  async getById(id: number): Promise<ApiDocument> {
    return this.requestJson("get", `/api/v1/documents/${id}`, { action: "getDocumentById" });
  }

  async update(id: number, document: ApiDocumentUpdateRequest): Promise<ApiDocument> {
    return this.requestJson("put", `/api/v1/documents/${id}`, { data: document, action: "updateDocument" });
  }

  async deleteById(id: number): Promise<void> {
    return this.requestVoid("delete", `/api/v1/documents/${id}`, { action: "deleteDocumentById" });
  }
}
