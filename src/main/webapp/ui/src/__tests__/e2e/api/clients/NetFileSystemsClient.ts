import type { APIRequestContext } from "@playwright/test";
import { env } from "@/__tests__/e2e/env";
import { assertOk } from "@/__tests__/e2e/responses";

export type NetFileSystemClientType = "SAMBA" | "SMBJ" | "SFTP" | "IRODS" | "S3";

export interface NetFileSystemSettings {
  name: string;
  url: string;
  clientType: NetFileSystemClientType;
  authType: "PASSWORD" | "PUBKEY" | "NONE";
  clientOptions: string;
  disabled: boolean;
  readAllowlist?: string;
  writeAllowlist?: string;
}

export interface NetFileSystem extends NetFileSystemSettings {
  id: number;
}

/** Sysadmin-session client for `/system/netfilesystem`; these web endpoints have no API-key equivalent. */
export class NetFileSystemsClient {
  constructor(private readonly request: APIRequestContext) {}

  async save(settings: NetFileSystemSettings & { id?: number }): Promise<number> {
    const response = await this.request.post("/system/netfilesystem/save", {
      headers: { Referer: env.baseURL },
      data: {
        ...settings,
        authOptions: "",
        readAllowlist: settings.readAllowlist ?? null,
        writeAllowlist: settings.writeAllowlist ?? null,
      },
    });
    await assertOk(response, "POST");
    return ((await response.json()) as { fileSystemId: number }).fileSystemId;
  }

  async list(): Promise<NetFileSystem[]> {
    const response = await this.request.get("/system/netfilesystem/ajax/list");
    await assertOk(response, "GET");
    return (await response.json()) as NetFileSystem[];
  }

  async delete(id: number): Promise<void> {
    const response = await this.request.post("/system/netfilesystem/delete", {
      headers: { Referer: env.baseURL },
      form: { fileSystemId: String(id) },
    });
    await assertOk(response, "POST");
    if ((await response.text()).trim() !== "true") {
      throw new Error(`Deleting file system ${id} was refused`);
    }
  }
}
