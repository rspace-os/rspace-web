import type { ApiDocument, ApiDocumentCreateRequest } from "../models/document";
import { BaseApiClient } from "./BaseApiClient";

/** Wraps `com.researchspace.api.v1.DocumentsApi` (`@RequestMapping("/api/v1/documents")`). */
export class DocumentsClient extends BaseApiClient {
  async create(doc: ApiDocumentCreateRequest): Promise<ApiDocument> {
    const res = await this.request.post("/api/v1/documents", {
      headers: this.headers(),
      data: doc,
    });
    await this.assertOk(res, "createDocument");
    return res.json() as Promise<ApiDocument>;
  }

  async getById(id: number): Promise<ApiDocument> {
    const res = await this.request.get(`/api/v1/documents/${id}`, {
      headers: this.headers(),
    });
    await this.assertOk(res, "getDocumentById");
    return res.json() as Promise<ApiDocument>;
  }

  async deleteById(id: number): Promise<void> {
    const res = await this.request.delete(`/api/v1/documents/${id}`, {
      headers: this.headers(),
    });
    await this.assertOk(res, "deleteDocumentById");
  }
}
