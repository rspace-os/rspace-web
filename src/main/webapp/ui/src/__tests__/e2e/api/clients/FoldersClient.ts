import type { ApiFolder, ApiFolderCreateRequest } from "../models/folder";
import { BaseApiClient } from "./BaseApiClient";

export class FoldersClient extends BaseApiClient {
  async create(folder: ApiFolderCreateRequest): Promise<ApiFolder> {
    return this.requestJson("post", "/api/v1/folders", { data: folder, action: "createFolder" });
  }
}
