import type { ApiFile, ApiFileUploadRequest } from "../models/files";
import { BaseApiClient } from "./BaseApiClient";

export class FilesClient extends BaseApiClient {
  async uploadFile({ name, mimeType, buffer, folderId, caption }: ApiFileUploadRequest): Promise<ApiFile> {
    const res = await this.request.post("/api/v1/files", {
      headers: this.headers(),
      multipart: {
        file: { name, mimeType, buffer },
        ...(folderId !== undefined ? { folderId: String(folderId) } : {}),
        ...(caption !== undefined ? { caption } : {}),
      },
    });
    await this.assertOk(res, "uploadFile");
    return res.json() as Promise<ApiFile>;
  }
}
