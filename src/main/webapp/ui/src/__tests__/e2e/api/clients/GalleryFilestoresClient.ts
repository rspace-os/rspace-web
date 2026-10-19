import { BaseApiClient } from "./BaseApiClient";

export interface GalleryFilestore {
  id: number;
  name: string;
  path: string;
}

/** A user's own filestores: named entry points into a sysadmin-configured file system. */
export class GalleryFilestoresClient extends BaseApiClient {
  async create({
    fileSystemId,
    name,
    path,
  }: {
    fileSystemId: number;
    name: string;
    path: string;
  }): Promise<GalleryFilestore> {
    const params = new URLSearchParams({ filesystemId: String(fileSystemId), name, pathToSave: path });
    return this.requestJson("post", `/api/v1/gallery/filestores?${params}`, { action: "createFilestore" });
  }

  async list(): Promise<GalleryFilestore[]> {
    return this.requestJson("get", "/api/v1/gallery/filestores", { action: "listFilestores" });
  }

  /** Logins are kept server-side per user, across browser sessions, until this is called. */
  async logoutFromFileSystem(fileSystemId: number): Promise<void> {
    await this.requestVoid("post", `/api/v1/gallery/filesystems/${fileSystemId}/logout`, {
      action: "logoutFromFileSystem",
    });
  }

  async delete(id: number): Promise<void> {
    await this.requestVoid("delete", `/api/v1/gallery/filestores/${id}`, { action: "deleteFilestore" });
  }
}
